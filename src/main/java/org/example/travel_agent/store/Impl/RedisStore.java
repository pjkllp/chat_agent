package org.example.travel_agent.store.Impl;

import lombok.RequiredArgsConstructor;
import org.example.travel_agent.dao.entity.AiChatMemoryEntity;
import org.example.travel_agent.store.Store;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RedisStore implements Store {

    public final StringRedisTemplate stringRedisTemplate;

    @Override
    public void append(List<AiChatMemoryEntity> messages){

    }

    @Override
    public List<AiChatMemoryEntity> load(){


        return null;
    }
}
