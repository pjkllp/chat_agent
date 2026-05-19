package org.example.travel_agent.service;

import org.example.travel_agent.dto.ChatRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface ChatService {

    void chatStream(ChatRequest requestParam, SseEmitter sse);
}
