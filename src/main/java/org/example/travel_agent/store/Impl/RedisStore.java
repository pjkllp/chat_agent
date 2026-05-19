package org.example.travel_agent.store.Impl;

import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.travel_agent.dao.entity.AiChatMemoryEntity;
import org.example.travel_agent.dao.mapper.AiChatMemoryMapper;
import org.example.travel_agent.store.Store;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisStore implements Store {

    private final StringRedisTemplate stringRedisTemplate;

    public final String ZSET_USER_CONVERSATION_KEY = "zset:user:message:%s:%s";

    public final String HASH_USER_CONVERSATION_KEY = "hash:user:message:%s:%s";

    public final AiChatMemoryMapper aiChatMemoryMapper;

    @Override
    public void append(List<AiChatMemoryEntity> messages, long userId, String conversationId) {
        String zsetKey = String.format(ZSET_USER_CONVERSATION_KEY, userId, conversationId);
        String hashKey = String.format(HASH_USER_CONVERSATION_KEY, userId, conversationId);
        for (AiChatMemoryEntity message : messages) {
            String msgId = IdUtil.getSnowflakeNextIdStr();
            message.setId(Long.parseLong(msgId));
            stringRedisTemplate.opsForZSet().add(zsetKey, msgId, System.currentTimeMillis());
            stringRedisTemplate.opsForHash().put(hashKey, msgId, JSON.toJSONString(message));
        }
        stringRedisTemplate.expire(zsetKey, 7, TimeUnit.DAYS);
        stringRedisTemplate.expire(hashKey, 7, TimeUnit.DAYS);
    }

    @Override
    public List<AiChatMemoryEntity> load(long userId, String conversationId, int count) {
        String zsetKey = String.format(ZSET_USER_CONVERSATION_KEY, userId, conversationId);
        String hashKey = String.format(HASH_USER_CONVERSATION_KEY, userId, conversationId);

        Long total = stringRedisTemplate.opsForZSet().size(zsetKey);
        count= Math.toIntExact(count == -1 ? total : count);
        if (total == null || total == 0) {
            return loadFromPgAndCache(userId, conversationId, count, zsetKey, hashKey);
        }

        long start = Math.max(0, total - count);
        long end = total - 1;

        Set<String> range = stringRedisTemplate.opsForZSet().range(zsetKey, start, end);

        ArrayList<AiChatMemoryEntity> messages = new ArrayList<>(count);
        if (range != null) {
            for (String msgId : range) {
                if (msgId != null) {
                    String msgStr = (String) stringRedisTemplate.opsForHash().get(hashKey, msgId);
                    if (msgStr != null) {
                        messages.add(JSON.parseObject(msgStr, AiChatMemoryEntity.class));
                    }
                }
            }
        }

        return messages;
    }

    private List<AiChatMemoryEntity> loadFromPgAndCache(long userId, String conversationId, int count,
                                                         String zsetKey, String hashKey) {

        List<AiChatMemoryEntity> entities = aiChatMemoryMapper.listByUserAndConversation(userId, conversationId);
        if (entities.isEmpty()) {
            return Collections.emptyList();
        }

        for (AiChatMemoryEntity entity : entities) {
            String msgId = String.valueOf(entity.getId());
            OffsetDateTime createTime = entity.getCreateTime();
            stringRedisTemplate.opsForZSet().add(zsetKey, msgId,
                    createTime != null
                            ? createTime.toInstant().toEpochMilli()
                            : System.currentTimeMillis());
            stringRedisTemplate.opsForHash().put(hashKey, msgId, JSON.toJSONString(entity));
        }
        stringRedisTemplate.expire(zsetKey, 7, TimeUnit.DAYS);
        stringRedisTemplate.expire(hashKey, 7, TimeUnit.DAYS);

        int size = entities.size();
        return entities.subList(Math.max(0, size - count), size);
    }

    @Override
    public void delete(long userId, String conversationId) {
        String zsetKey = String.format(ZSET_USER_CONVERSATION_KEY, userId, conversationId);
        String hashKey = String.format(HASH_USER_CONVERSATION_KEY, userId, conversationId);
        stringRedisTemplate.delete(List.of(zsetKey, hashKey));
    }

    @Override
    public List<String> listUserConversationIds(Long userId) {
        String pattern = String.format("zset:user:message:%d:*", userId);
        List<String> conversationIds = new ArrayList<>();
        try (Cursor<String> cursor = stringRedisTemplate.scan(
                ScanOptions.scanOptions().match(pattern).count(100).build())) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                String[] parts = key.split(":");
                if (parts.length >= 5) {
                    conversationIds.add(parts[4]);
                }
            }
        } catch (Exception e) {
            log.warn("RedisStore.listUserConversationIds: scan failed, userId={}", userId, e);
        }
        return conversationIds;
    }
}
