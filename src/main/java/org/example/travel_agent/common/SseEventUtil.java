package org.example.travel_agent.common;

import com.alibaba.cloud.ai.graph.OverAllState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.travel_agent.service.AgentTraceService;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SseEventUtil {

    private final SseEmitterRegistry sseEmitterRegistry;
    private final AgentTraceService agentTraceService;

    public void sendNodeStatus(OverAllState state, String node, String status, String message) throws IOException {
        SseEmitter sse = resolveEmitter(state);
        String safeMessage = message == null ? "" : message;

        if (sse != null) {
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

        try {
            String conversationId = state.value("conversationId", "");
            Object userIdObj = state.value("userId").orElse(null);
            Long userId = userIdObj instanceof Long v ? v : null;
            Object messageIdObj = state.value("messageId").orElse(null);
            Long messageId = messageIdObj instanceof Long v ? v : null;
            if (conversationId != null && !conversationId.isBlank()) {
                switch (status) {
                    case "start" -> agentTraceService.recordStart(conversationId, userId, messageId, node);
                    case "finish" -> agentTraceService.recordFinish(conversationId, messageId, node, safeMessage);
                    case "error" -> agentTraceService.recordError(conversationId, userId, messageId, node, safeMessage);
                    case "cancel" -> agentTraceService.recordCancel(conversationId, userId, messageId, node, safeMessage);
                }
            }
        } catch (Exception e) {
            log.warn("SseEventUtil: failed to record trace for node={}, status={}", node, status, e);
        }
    }

    /**
     * 节点异常兜底：把异常落成一条 error trace，供调用方随后重新抛出以终止工作流。
     * 只取异常自身的 message（没有则退回类名），不吞异常——异常由调用方继续向上抛。
     */
    public void markNodeError(OverAllState state, String node, Throwable error) {
        String message;
        if (error == null) {
            message = "节点执行异常";
        } else if (error.getMessage() == null || error.getMessage().isBlank()) {
            message = error.getClass().getSimpleName();
        } else {
            message = error.getMessage();
        }
        try {
            sendNodeStatus(state, node, "error", message);
        } catch (IOException e) {
            log.warn("markNodeError: failed to send error status for node={}", node, e);
        }
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

    /**
     * 本轮（messageId）是否已被用户取消。节点入口和流式分片都靠它做拦截。
     */
    public boolean isCancelled(OverAllState state) {
        Long messageId = state.value("messageId", Long.class).orElse(null);
        return sseEmitterRegistry.isCancel(messageId);
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
