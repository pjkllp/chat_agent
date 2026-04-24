package org.example.travel_agent.controller;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.example.travel_agent.common.SseEmitterRegistry;
import org.example.travel_agent.constant.JwtAuthConstants;
import org.example.travel_agent.dto.DeepThinkRequest;
import org.example.travel_agent.service.ChatService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.UUID;

@RequestMapping("/api/chat")
@RestController
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/deepThink")
    public SseEmitter deepThink(@RequestBody DeepThinkRequest requestParam) {

        SseEmitter sse=new SseEmitter(0L);

        chatService.deepThink(requestParam,sse);

        return sse;
    }
}
