package org.example.travel_agent.service;

import org.example.travel_agent.dto.ChatRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface ChatService {

    void chatStream(ChatRequest requestParam, SseEmitter sse);

    /**
     * 取消某一轮对话。只有该轮仍在进行中且属于该用户时才生效。
     *
     * @return true 表示成功打上取消标记，false 表示该轮已结束或不属于该用户
     */
    boolean cancelChat(Long messageId, Long userId);
}
