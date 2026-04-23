package org.example.travel_agent.controller;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RequestMapping("/api/chat")
@RestController
@RequiredArgsConstructor
public class ChatController {

    private final CompiledGraph deepThinkGraph;

    @GetMapping("/deepThink")
    public SseEmitter deepThink(@RequestParam("question")String originalQuestion,
                                @RequestParam("conversationId")String conversationId){
        SseEmitter sse = new SseEmitter(0L);
        try {
            deepThinkGraph.invoke(
                    Map.of(
                            "sse", sse,
                            "original_question", originalQuestion,
                            "conversationId",conversationId
                    )
            );
        } catch (Exception e) {
            sse.completeWithError(e);
        }
        return sse;
    }
}
