package org.example.travel_agent.common;

import com.alibaba.cloud.ai.graph.OverAllState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SseEventUtil {

    private final SseEmitterRegistry sseEmitterRegistry;

    public void sendNodeStatus(OverAllState state, String node, String status, String message) throws IOException {
        SseEmitter sse = resolveEmitter(state);
        if (sse == null) {
            return;
        }
        String safeMessage = message == null ? "" : message;
        sse.send(
                SseEmitter.event()
                        .name("workflow")
                        .data(Map.of(
                                "type", "node_status",
                                "node", node,
                                "status", status,
                                "message", safeMessage,
                                "timestamp", Instant.now().toString()
                        ))
        );
    }

    public void sendAnswerChunk(OverAllState state, String data) throws IOException {
        SseEmitter sse = resolveEmitter(state);
        if (sse == null) {
            return;
        }
        sse.send(SseEmitter.event().name("answer").data(data));
    }

    public boolean hasEmitter(OverAllState state) {
        return resolveEmitter(state) != null;
    }

    public void complete(OverAllState state) {
        String executionId = state.value("conversationId", "");
        SseEmitter sse = resolveEmitter(state);
        if (sse != null) {
            sse.complete();
        }
        sseEmitterRegistry.remove(executionId);
    }

    private SseEmitter resolveEmitter(OverAllState state) {
        String conversationId = state.value("conversationId", "");
        if (conversationId == null || conversationId.isBlank()) {
            return null;
        }
        return sseEmitterRegistry.get(conversationId);
    }
}
