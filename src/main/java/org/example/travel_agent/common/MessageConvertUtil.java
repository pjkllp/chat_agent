package org.example.travel_agent.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.travel_agent.dao.entity.AiChatMemoryEntity;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public final class MessageConvertUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();

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

    /**
     * @param messageId 本轮对话的标识；落到 ASSISTANT 那行作为主键，使其与 trace 的 message_id 一致。
     *                  为 null 时（如简单问答链路）由存储层自行生成雪花 id。
     */
    public static List<AiChatMemoryEntity> toEntities(List<Message> messages, Long userId, String conversationId,
                                                      Long messageId) {
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
            entity.setCreateTime(OffsetDateTime.now());

            Object attachments = message.getMetadata() == null
                    ? null
                    : message.getMetadata().get("attachments");
            if (attachments != null) {
                try {
                    entity.setAttachmentJson(MAPPER.writeValueAsString(attachments));
                } catch (Exception e) {
                    // 序列化失败不影响消息落库
                    log.warn("serialize attachments failed, content={}", message.getText(), e);
                }
            }

            if (message instanceof UserMessage) {
                entity.setMessageType("USER");
            } else if (message instanceof AssistantMessage) {
                entity.setMessageType("ASSISTANT");
                // 本轮 AI 回复沿用请求入口预生成的 id，使 t_agent_trace.message_id 能直接关联到这条消息
                if (messageId != null) {
                    entity.setId(messageId);
                }
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
