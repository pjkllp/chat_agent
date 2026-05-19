package org.example.travel_agent.store;

import org.example.travel_agent.dao.entity.AiChatMemoryEntity;

import java.util.List;

public interface Store {

    void append(List<AiChatMemoryEntity> messages, long userId, String conversationId);

    List<AiChatMemoryEntity> load(long userId, String conversationId, int count);

    void delete(long userId, String conversationId);

    List<String> listUserConversationIds(Long userId);
}
