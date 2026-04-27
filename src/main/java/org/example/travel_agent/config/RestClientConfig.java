package org.example.travel_agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient restClient() {
        // 这里可以配置默认的 baseUrl、拦截器等
        return RestClient.create();
    }
}