package org.example.travel_agent.memory;

import org.example.travel_agent.dao.entity.AiChatMemoryEntity;
import org.example.travel_agent.dto.ConversationSummary;

import java.time.OffsetDateTime;
import java.util.List;

public interface LLMMemory {

    void save(List<AiChatMemoryEntity> messages, Long userId, String conversationId);

    List<AiChatMemoryEntity> getMemory(Long userId, String conversationId, int count);

    void delete(Long userId, String conversationId);

    List<ConversationSummary> listConversationsByUser(Long userId, OffsetDateTime startTime, OffsetDateTime endTime);
}
