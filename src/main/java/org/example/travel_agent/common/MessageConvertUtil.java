package org.example.travel_agent.common;

import org.example.travel_agent.dao.entity.AiChatMemoryEntity;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class MessageConvertUtil {

    private MessageConvertUtil() {
    }

    public static List<Message> toMessages(List<AiChatMemoryEntity> records) {
        List<Message> messages = new ArrayList<>();
        if (records == null || records.isEmpty()) {
            return messages;
        }
        for (AiChatMemoryEntity record : records) {
            if (record == null || record.getMessageType() == null || record.getContent() == null) {
                continue;
            }
            String type = record.getMessageType().trim().toUpperCase();
            String content = record.getContent();
            switch (type) {
                case "USER" -> messages.add(new UserMessage(content));
                case "ASSISTANT" -> messages.add(new AssistantMessage(content));
                case "SYSTEM" -> messages.add(new SystemMessage(content));
                default -> {
                }
            }
        }
        return messages;
    }

    public static List<AiChatMemoryEntity> toEntities(List<Message> messages, Long userId, String conversationId) {
        List<AiChatMemoryEntity> entities = new ArrayList<>();
        if (messages == null || messages.isEmpty()) {
            return entities;
        }
        Long safeUserId = userId;
        String safeConversationId = conversationId == null ? "" : conversationId;

        for (Message message : messages) {
            if (message == null || message.getText() == null || message.getText().isBlank()) {
                continue;
            }
            AiChatMemoryEntity entity = new AiChatMemoryEntity();
            entity.setUserId(safeUserId);
            entity.setConversationId(safeConversationId);
            entity.setContent(message.getText());
            entity.setCreateTime(LocalDateTime.now());

            if (message instanceof UserMessage) {
                entity.setMessageType("USER");
            } else if (message instanceof AssistantMessage) {
                entity.setMessageType("ASSISTANT");
            } else if (message instanceof SystemMessage) {
                entity.setMessageType("SYSTEM");
            } else {
                continue;
            }
            entities.add(entity);
        }
        return entities;
    }
}
