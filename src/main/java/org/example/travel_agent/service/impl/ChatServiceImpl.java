package org.example.travel_agent.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import lombok.RequiredArgsConstructor;
import org.example.travel_agent.common.SseEmitterRegistry;
import org.example.travel_agent.common.UserContext;
import org.example.travel_agent.dto.DeepThinkRequest;
import org.example.travel_agent.service.ChatService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final CompiledGraph deepThinkGraph;

    private final SseEmitterRegistry sseEmitterRegistry;
    @Qualifier("chatAsyncExecutor")
    private final Executor chatAsyncExecutor;

    @Override
    public void deepThink(DeepThinkRequest requestParam, SseEmitter sse) {

        String originalQuestion = requestParam.getQuestion();
        String conversationId = requestParam.getConversationId();
        long userId = UserContext.get().getId();
        conversationId=conversationId!=null&&!conversationId.isBlank()?
                conversationId: IdUtil.getSnowflakeNextIdStr();
        String finalConversationId=conversationId;

        sseEmitterRegistry.put(conversationId, sse);

        sse.onCompletion(() -> sseEmitterRegistry.remove(finalConversationId));
        sse.onTimeout(() -> sseEmitterRegistry.remove(finalConversationId));
        sse.onError((ex) -> sseEmitterRegistry.remove(finalConversationId));
        CompletableFuture.runAsync(() -> {
            try {
                deepThinkGraph.invoke(
                        Map.of(
                                "original_question", originalQuestion,
                                "conversationId", finalConversationId,
                                "userId",userId
                        )
                );
            } catch (Exception e) {
                sse.completeWithError(e);
                sseEmitterRegistry.remove(finalConversationId);
            }
        }, chatAsyncExecutor);
    }
}
