package org.example.travel_agent.common;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SseEmitterRegistry {

    private final Map<String, SseEmitter> emitterMap = new ConcurrentHashMap<>();

    public void put(String conversationId, SseEmitter emitter) {
        if (conversationId == null || conversationId.isBlank() || emitter == null) {
            return;
        }
        emitterMap.put(conversationId, emitter);
    }

    public SseEmitter get(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return null;
        }
        return emitterMap.get(conversationId);
    }

    public void remove(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }
        emitterMap.remove(conversationId);
    }
}
