package org.example.travel_agent.config;

import com.alibaba.ttl.threadpool.TtlExecutors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class ThreadPoolConfig {

    @Bean("chatAsyncExecutor")
    public Executor chatAsyncExecutor() {
        ThreadPoolExecutor delegate = new ThreadPoolExecutor(
                8,
                32,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1024),
                runnable -> {
                    Thread thread = new Thread(runnable);
                    thread.setName("chat-async-" + thread.threadId());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        return TtlExecutors.getTtlExecutor(delegate);
    }

    @Bean("knowledgeExecutor")
    public Executor knowledgeExecutor(){
        ThreadPoolExecutor delegate = new ThreadPoolExecutor(
                8,
                32,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1024),
                runnable -> {
                    Thread thread = new Thread(runnable);
                    thread.setName("chat-async-" + thread.threadId());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        return TtlExecutors.getTtlExecutor(delegate);
    }
}
