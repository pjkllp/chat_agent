package org.example.travel_agent.store.Impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.travel_agent.dao.entity.AiChatMemoryEntity;
import org.example.travel_agent.dao.mapper.AiChatMemoryMapper;
import org.example.travel_agent.store.Store;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PgsqlStore implements Store {

    private final AiChatMemoryMapper aiChatMemoryMapper;

    @Override
    public void append(List<AiChatMemoryEntity> messages, long userId, String conversationId) {
        for (AiChatMemoryEntity message : messages) {
            try {
                // id 为空时 IdType.ASSIGN_ID 在 insert 时生成雪花 id；
                // 已有 id（RedisStore 生成，或 AI 回复沿用的 messageId）则原样写入。
                aiChatMemoryMapper.insert(message);
            } catch (Exception e) {
                log.warn("PgsqlStore.append: insert failed for conversationId={}", conversationId, e);
            }
        }
    }

    @Override
    public List<AiChatMemoryEntity> load(long userId, String conversationId, int count) {
        return aiChatMemoryMapper.selectList(
                Wrappers.<AiChatMemoryEntity>lambdaQuery()
                        .eq(AiChatMemoryEntity::getUserId, userId)
                        .eq(AiChatMemoryEntity::getConversationId, conversationId)
                        .orderByDesc(AiChatMemoryEntity::getCreateTime)
                        .last("LIMIT " + count)
        );
    }

    @Override
    public void delete(long userId, String conversationId) {
        aiChatMemoryMapper.deleteByConversationId(userId, conversationId);
    }

    @Override
    public List<String> listUserConversationIds(Long userId) {
        return aiChatMemoryMapper.selectConversationIdsByUserId(userId);
    }
}
