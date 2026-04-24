package org.example.travel_agent.config;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class BloomFilterConfig {

    private final RedissonClient redissonClient;

    @Bean
    public RBloomFilter<String> usernameRBloomFilter(){
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter("username:bloom");
        bloomFilter.tryInit(1000000L,0.001);
        return bloomFilter;
    }
}
