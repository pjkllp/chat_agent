package org.example.travel_agent.common;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SseEmitterRegistry {

    // 一个会话同时只允许存在一条进行中的流，key 用 conversationId。
    private final Map<String, Turn> turns = new ConcurrentHashMap<>();

    // 取消标记按 messageId（轮次）记录：节点每次进入、流式每次收到分片时读取。
    private final Map<Long, Long> cancelSet = new ConcurrentHashMap<>();

    private record Turn(Long messageId, Long userId, SseEmitter emitter) {
    }

    public boolean put(String conversationId, Long messageId, Long userId, SseEmitter emitter) {
        if (conversationId == null || conversationId.isBlank() || messageId == null || emitter == null) {
            return false;
        }
        return turns.putIfAbsent(conversationId, new Turn(messageId, userId, emitter)) == null;
    }

    /**
     * 给某一轮打取消标记。只有该轮仍在进行中、且属于调用者时才生效，
     * 因此不会留下“轮次早已结束、标记却还挂着”的过期标记。
     */
    public boolean cancel(Long messageId, Long userId) {
        if (messageId == null) {
            return false;
        }
        for (Turn turn : turns.values()) {
            if (messageId.equals(turn.messageId())
                    && (userId == null || userId.equals(turn.userId()))) {
                cancelSet.put(messageId, System.currentTimeMillis());
                return true;
            }
        }
        return false;
    }

    public boolean isCancel(Long messageId) {
        return messageId != null && cancelSet.containsKey(messageId);
    }

    public SseEmitter get(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return null;
        }
        Turn turn = turns.get(conversationId);
        return turn == null ? null : turn.emitter();
    }

    /**
     * 结束某会话的当前轮：关闭 emitter 并清掉该轮的取消标记。
     * 标记按 messageId 记录，所以必须先从 Turn 取回 messageId 才能清干净。
     */
    public void remove(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }
        Turn turn = turns.remove(conversationId);
        if (turn == null) {
            return;
        }
        cancelSet.remove(turn.messageId());
        try {
            turn.emitter().complete();
        } catch (Exception e) {
            // 客户端已断开或重复 complete 时忽略，emitter 的关闭是幂等语义
        }
    }
}
