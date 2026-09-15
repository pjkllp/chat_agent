package org.example.travel_agent.service.impl;

import cn.hutool.core.util.IdUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.travel_agent.Exceptions.CancelException;
import org.example.travel_agent.Exceptions.ClientException;
import org.example.travel_agent.common.SseEmitterRegistry;
import org.example.travel_agent.common.UserContext;
import org.example.travel_agent.dto.ChatAttachmentDTO;
import org.example.travel_agent.dto.ChatRequest;
import org.example.travel_agent.service.ChatAttachmentSupport;
import org.example.travel_agent.service.ChatService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final CompiledGraph deepThinkGraph;

    private final SseEmitterRegistry sseEmitterRegistry;

    private final Executor chatAsyncExecutor;

    private final ChatClient chatClient;

    private final ChatAttachmentSupport chatAttachmentSupport;

    @Value("${app.chat.vision-model:qwen-vl-max}")
    private String visionModel;

    @Override
    public void
    chatStream(ChatRequest requestParam, SseEmitter sse) {

        String originalQuestion = requestParam.getQuestion();
        String conversationId = requestParam.getConversationId();
        int isDeepThink = requestParam.getIsDeepThink();
        long userId = UserContext.get().getId();
        conversationId=conversationId!=null&&!conversationId.isBlank()?
                conversationId: IdUtil.getSnowflakeNextIdStr();
        String finalConversationId=conversationId;
        // 本轮对话的唯一标识：trace 各节点行共用，同时作为该轮 AI 回复落库时的主键。
        // 必须在这里生成——最早写 trace 的 rewrite_node 已经在用，晚于此就没有可用的值。
        long messageId = IdUtil.getSnowflakeNextId();

        List<ChatAttachmentDTO> attachments = requestParam.getAttachments();
        try {
            chatAttachmentSupport.validate(attachments);
        } catch (ClientException e) {
            log.warn("[chatStream] invalid attachments, conversationId={}, msg={}", finalConversationId, e.getMessage());
            sse.completeWithError(e);
            return;
        }

        boolean result = sseEmitterRegistry.put(conversationId, messageId, userId, sse);
        if (!result) {
            log.warn("[chatStream] conversationId already exists, conversationId={}", finalConversationId);
            sse.completeWithError(new ClientException("conversationId already exists"));
            return;
        }
        sse.onCompletion(() -> sseEmitterRegistry.remove(finalConversationId));
        sse.onTimeout(() -> sseEmitterRegistry.remove(finalConversationId));
        sse.onError((ex) -> sseEmitterRegistry.remove(finalConversationId));

        // 先告诉前端本轮 messageId，取消接口需要它来定位要中止的那一轮。
        // 此时 emitter 的 handler 还没初始化，Spring 会缓冲这条事件，等 controller 返回后再发。
        try {
            sse.send(SseEmitter.event().name("turn").data(Map.of(
                    "messageId", messageId,
                    "conversationId", finalConversationId
            )));
        } catch (IOException e) {
            log.warn("[chatStream] send turn event failed, conversationId={}, msg={}", finalConversationId, e.getMessage());
        }

        if(isDeepThink!=1){
            ChatClient.ChatClientRequestSpec promptSpec = chatClient.prompt()
                    .system("你是一名可爱的用户助手，请帮助用户解决问题")
                    .messages(chatAttachmentSupport.buildUserMessage(originalQuestion, attachments));
            if (chatAttachmentSupport.hasImage(attachments)) {
                // multiModel=true 才会走多模态端点，否则图片被发往文本端点并报 url error
                promptSpec.options(DashScopeChatOptions.builder().model(visionModel).temperature(0.1).multiModel(true).build());
            }
            promptSpec.stream()
                    .content()
                    // takeWhile 会真正 dispose 上游：doOnNext 里 return 只是退出这一次回调，流还会继续跑
                    .takeWhile(content -> !sseEmitterRegistry.isCancel(messageId))
                    .doOnNext(content -> {
                        try {
                            sse.send(content);
                        } catch (Exception e) {
                            log.error("[chatStream] send message failed, conversationId={}, msg={}", finalConversationId, e.getMessage(), e);
                            sse.completeWithError(e);
                            sseEmitterRegistry.remove(finalConversationId);
                        }
                    }).doOnComplete(() -> {
                        log.info("[chatStream] chat completed, conversationId={}", finalConversationId);
                        sse.complete();
                        sseEmitterRegistry.remove(finalConversationId);
                    }).doOnError(e -> {
                        log.error("[chatStream] chat failed, conversationId={}, msg={}", finalConversationId, e.getMessage(), e);
                        sse.completeWithError(e);
                        sseEmitterRegistry.remove(finalConversationId);
                    }).subscribe();
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                log.info("[deepThink] start invoke, conversationId={}, userId={}, messageId={}", finalConversationId, userId, messageId);
                deepThinkGraph.invoke(
                        Map.of(
                                "original_question", originalQuestion,
                                "conversationId", finalConversationId,
                                "userId",userId,
                                "messageId",messageId,
                                "attachments", attachments == null ? List.of() : attachments
                        )
                );
                log.info("[deepThink] invoke finished, conversationId={}", finalConversationId);
            } catch (Exception e) {
                if (isCancelledByUser(e)) {
                    // 用户主动取消属于正常中止，不向下推错误，也不用 markNodeError 记一条失败
                    log.info("[deepThink] canceled by user, conversationId={}, messageId={}", finalConversationId, messageId);
                    sse.complete();
                    sseEmitterRegistry.remove(finalConversationId);
                    return;
                }
                log.error("[deepThink] invoke failed, conversationId={}, messageId={}, msg={}", finalConversationId, messageId, e.getMessage(), e);
                sse.completeWithError(e);
                sseEmitterRegistry.remove(finalConversationId);
            }
        }, chatAsyncExecutor);
    }

    @Override
    public boolean cancelChat(Long messageId, Long userId) {
        return sseEmitterRegistry.cancel(messageId, userId);
    }

    /** CancelException 可能被工作流层层包装，所以要顺着 cause 链找。 */
    private boolean isCancelledByUser(Throwable error) {
        Throwable cur = error;
        while (cur != null) {
            if (cur instanceof CancelException) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }
}
