package org.example.travel_agent.memory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.travel_agent.dao.entity.AiChatMemoryEntity;
import org.example.travel_agent.dto.ConversationSummary;
import org.example.travel_agent.store.Store;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class LLMMemoryImpl implements LLMMemory {

    private final Store redisStore;

    private final Store pgsqlStore;

    @Override
    public void save(List<AiChatMemoryEntity> messages, Long userId, String conversationId) {
        redisStore.append(messages, userId, conversationId);
        pgsqlStore.append(messages, userId, conversationId);
    }

    @Override
    public List<AiChatMemoryEntity> getMemory(Long userId, String conversationId, int count) {
        List<AiChatMemoryEntity> messages = redisStore.load(userId, conversationId, count);
        if (messages == null || messages.isEmpty()) {
            messages = pgsqlStore.load(userId, conversationId, count);
            if (messages != null && !messages.isEmpty()) {
                redisStore.append(messages, userId, conversationId);
            }
        }
        return messages;
    }

    @Override
    public void delete(Long userId, String conversationId) {
        redisStore.delete(userId, conversationId);
        pgsqlStore.delete(userId, conversationId);
    }

    @Override
    public List<ConversationSummary> listConversationsByUser(Long userId, OffsetDateTime startTime, OffsetDateTime endTime) {
        Set<String> allIds = new LinkedHashSet<>();
        allIds.addAll(pgsqlStore.listUserConversationIds(userId));
        allIds.addAll(redisStore.listUserConversationIds(userId));

        List<ConversationSummary> summaries = new ArrayList<>();
        for (String convId : allIds) {
            List<AiChatMemoryEntity> msgs = pgsqlStore.load(userId, convId, 1);
            if (msgs.isEmpty()) {
                msgs = redisStore.load(userId, convId, 1);
            }
            if (!msgs.isEmpty()) {
                AiChatMemoryEntity first = msgs.get(0);
                OffsetDateTime msgTime = first.getCreateTime();
                if (msgTime != null && !msgTime.isBefore(startTime) && !msgTime.isAfter(endTime)) {
                    summaries.add(ConversationSummary.builder()
                            .conversationId(convId)
                            .userId(userId)
                            .firstMsgTime(msgTime)
                            .lastMsgTime(msgTime)
                            .msgCount(1L)
                            .build());
                }
            }
        }

        summaries.sort((a, b) -> {
            if (a.getLastMsgTime() == null || b.getLastMsgTime() == null) return 0;
            return b.getLastMsgTime().compareTo(a.getLastMsgTime());
        });
        return summaries;
    }
}
