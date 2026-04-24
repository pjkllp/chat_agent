package org.example.travel_agent.service;

import org.example.travel_agent.dto.DeepThinkRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface ChatService {

    void deepThink(DeepThinkRequest requestParam,SseEmitter sse);
}
