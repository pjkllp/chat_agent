package org.example.travel_agent.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.travel_agent.dao.entity.UserEntity;
import org.example.travel_agent.dao.mapper.UserMapper;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class BloomFilterConfig {

    private final RedissonClient redissonClient;
    private final UserMapper userMapper;

    @Bean
    public RBloomFilter<String> usernameRBloomFilter() {
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter("username:bloom");
        bloomFilter.tryInit(1000000L, 0.001);
        return bloomFilter;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void preloadBloomFilter() {
        RBloomFilter<String> bloomFilter = usernameRBloomFilter();
        List<UserEntity> users = userMapper.selectList(Wrappers.lambdaQuery(UserEntity.class));
        for (UserEntity user : users) {
            bloomFilter.add(user.getUsername());
        }
        log.info("Bloom filter preloaded with {} usernames", users.size());
    }
}
