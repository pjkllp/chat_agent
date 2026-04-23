package org.example.travel_agent.service.impl;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ConversationStreamConsumer {

    private final StringRedisTemplate stringRedisTemplate;

}
