package org.example.travel_agent.store.Impl;

import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import org.example.travel_agent.dao.entity.AiChatMemoryEntity;
import org.example.travel_agent.store.Store;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RedisStore implements Store {

    public final StringRedisTemplate stringRedisTemplate;

    public final String ZSET_USER_CONVERSATION_KEY="zset:user:message:%s:%s";

    public final String HASH_USER_CONVERSATION_KEY="hash:user:message:%s:%s";

    @Override
    public void append(List<AiChatMemoryEntity> messages,long userId,String conversationId){

        String zsetKey = String.format(ZSET_USER_CONVERSATION_KEY, userId, conversationId);
        String hashKey= String.format(HASH_USER_CONVERSATION_KEY,userId,conversationId);
        for (AiChatMemoryEntity message : messages){
            String msgId = IdUtil.getSnowflakeNextIdStr();
            stringRedisTemplate.opsForZSet().add(
                    zsetKey,
                    msgId,
                    System.currentTimeMillis()
            );
            stringRedisTemplate.opsForHash().put(
                    hashKey,
                    msgId,
                    JSON.toJSONString(message)
            );
        }
        stringRedisTemplate.expire(zsetKey,7, TimeUnit.DAYS);
        stringRedisTemplate.expire(hashKey,7,TimeUnit.DAYS);
    }

    @Override
    public List<AiChatMemoryEntity> load(long userId,String conversationId,int count){

        String zsetKey = String.format(ZSET_USER_CONVERSATION_KEY, userId, conversationId);
        String hashKey= String.format(HASH_USER_CONVERSATION_KEY,userId,conversationId);

        Set<String> range = stringRedisTemplate.opsForZSet().range(zsetKey, 0, (count-1)* 2L);

        ArrayList<AiChatMemoryEntity> messages = new ArrayList<>(count);
        if (range != null) {
            for (String msgId :range){
                String msgStr = null;
                if (msgId != null) {
                    msgStr = (String) stringRedisTemplate.opsForHash().get(hashKey, msgId);
                    messages.add(JSON.parseObject(msgStr, AiChatMemoryEntity.class));
                }
            }
        }

        return messages;
    }
}
