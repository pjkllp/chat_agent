package org.example.travel_agent.service.impl;

import cn.hutool.core.util.IdUtil;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.travel_agent.common.SseEmitterRegistry;
import org.example.travel_agent.common.UserContext;
import org.example.travel_agent.dto.ChatRequest;
import org.example.travel_agent.service.ChatService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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

    @Override
    public void chatStream(ChatRequest requestParam, SseEmitter sse) {

        String originalQuestion = requestParam.getQuestion();
        String conversationId = requestParam.getConversationId();
        int isDeepThink = requestParam.getIsDeepThink();
        long userId = UserContext.get().getId();
        conversationId=conversationId!=null&&!conversationId.isBlank()?
                conversationId: IdUtil.getSnowflakeNextIdStr();
        String finalConversationId=conversationId;

        sseEmitterRegistry.put(conversationId, sse);

        if(isDeepThink!=1){
            chatClient.prompt()
                    .system("你是一名可爱的用户助手，请帮助用户解决问题")
                    .user(originalQuestion)
                    .stream()
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
        }

        sse.onCompletion(() -> sseEmitterRegistry.remove(finalConversationId));
        sse.onTimeout(() -> sseEmitterRegistry.remove(finalConversationId));
        sse.onError((ex) -> sseEmitterRegistry.remove(finalConversationId));
        CompletableFuture.runAsync(() -> {
            try {
                log.info("[deepThink] start invoke, conversationId={}, userId={}", finalConversationId, userId);
                deepThinkGraph.invoke(
                        Map.of(
                                "original_question", originalQuestion,
                                "conversationId", finalConversationId,
                                "userId",userId
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
