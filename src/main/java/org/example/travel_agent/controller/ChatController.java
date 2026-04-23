package org.example.travel_agent.controller;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import lombok.RequiredArgsConstructor;
import org.example.travel_agent.common.SseEmitterRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.UUID;

@RequestMapping("/api/chat")
@RestController
@RequiredArgsConstructor
public class ChatController {

    private final CompiledGraph deepThinkGraph;
    private final SseEmitterRegistry sseEmitterRegistry;

    @GetMapping("/deepThink")
    public SseEmitter deepThink(@RequestParam("question")String originalQuestion,
                                @RequestParam("conversationId")String conversationId,
                                @RequestParam("userId")String userId){
        SseEmitter sse = new SseEmitter(0L);
        String executionId = UUID.randomUUID().toString();
        sseEmitterRegistry.put(executionId, sse);
        sse.onCompletion(() -> sseEmitterRegistry.remove(executionId));
        sse.onTimeout(() -> sseEmitterRegistry.remove(executionId));
        sse.onError((ex) -> sseEmitterRegistry.remove(executionId));
        CompletableFuture.runAsync(() -> {
            try {
                deepThinkGraph.invoke(
                        Map.of(
                                "execution_id", executionId,
                                "original_question", originalQuestion,
                                "conversationId", conversationId,
                                "userId", userId
                        )
                );
            } catch (Exception e) {
                sse.completeWithError(e);
                sseEmitterRegistry.remove(executionId);
            }
        });
        return sse;
    }
}
