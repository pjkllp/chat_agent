package org.example.travel_agent.service;

import org.example.travel_agent.dao.entity.AiChatMemoryEntity;

import java.util.List;

public interface AiChatMemoryService {

    void saveMessage(AiChatMemoryEntity message);

    List<AiChatMemoryEntity> listByUserAndConversation(Long userId, String conversationId);
}
