package org.example.travel_agent.service.impl;

import cn.hutool.core.util.IdUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    public void chatStream(ChatRequest requestParam, SseEmitter sse) {

        String originalQuestion = requestParam.getQuestion();
        String conversationId = requestParam.getConversationId();
        int isDeepThink = requestParam.getIsDeepThink();
        long userId = UserContext.get().getId();
        conversationId=conversationId!=null&&!conversationId.isBlank()?
                conversationId: IdUtil.getSnowflakeNextIdStr();
        String finalConversationId=conversationId;

        List<ChatAttachmentDTO> attachments = requestParam.getAttachments();
        try {
            chatAttachmentSupport.validate(attachments);
        } catch (ClientException e) {
            log.warn("[chatStream] invalid attachments, conversationId={}, msg={}", finalConversationId, e.getMessage());
            sse.completeWithError(e);
            return;
        }

        sseEmitterRegistry.put(conversationId, sse);
        sse.onCompletion(() -> sseEmitterRegistry.remove(finalConversationId));
        sse.onTimeout(() -> sseEmitterRegistry.remove(finalConversationId));
        sse.onError((ex) -> sseEmitterRegistry.remove(finalConversationId));

        if(isDeepThink!=1){
            ChatClient.ChatClientRequestSpec promptSpec = chatClient.prompt()
                    .system("你是一名可爱的用户助手，请帮助用户解决问题")
                    .messages(chatAttachmentSupport.buildUserMessage(originalQuestion, attachments));
            if (chatAttachmentSupport.hasImage(attachments)) {
                promptSpec.options(DashScopeChatOptions.builder().model(visionModel).temperature(0.1).build());
            }
            promptSpec.stream()
                    .content()
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
                log.info("[deepThink] start invoke, conversationId={}, userId={}", finalConversationId, userId);
                deepThinkGraph.invoke(
                        Map.of(
                                "original_question", originalQuestion,
                                "conversationId", finalConversationId,
                                "userId",userId,
                                "attachments", attachments == null ? List.of() : attachments
                        )
                );
                log.info("[deepThink] invoke finished, conversationId={}", finalConversationId);
            } catch (Exception e) {
                log.error("[deepThink] invoke failed, conversationId={}, msg={}", finalConversationId, e.getMessage(), e);
                sse.completeWithError(e);
                sseEmitterRegistry.remove(finalConversationId);
            }
        }, chatAsyncExecutor);
    }
}
