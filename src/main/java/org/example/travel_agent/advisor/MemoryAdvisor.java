package org.example.travel_agent.advisor;

import lombok.RequiredArgsConstructor;
import org.example.travel_agent.dao.entity.AiChatMemoryEntity;
import org.example.travel_agent.store.Store;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class MemoryAdvisor implements CallAdvisor, StreamAdvisor {

    public final Store redisStore;


    @NotNull
    @Override
    public ChatClientResponse adviseCall(@NotNull ChatClientRequest chatClientRequest, @NotNull CallAdvisorChain callAdvisorChain) {

        String conversationId =(String) chatClientRequest.context().get("conversationId");

        if (conversationId==null||conversationId.isBlank()){
            return callAdvisorChain.nextCall(chatClientRequest);
        }

        List<UserMessage> userMessages = chatClientRequest.prompt().getUserMessages();

        loadAndAppend(null,conversationId);


        return null;
    }

    private List<AiChatMemoryEntity> loadAndAppend(List<AiChatMemoryEntity> messages,String conversationId){

        redisStore.append(messages);

        return redisStore.load();
    }

    @NotNull
    @Override
    public Flux<ChatClientResponse> adviseStream(@NotNull ChatClientRequest chatClientRequest, @NotNull StreamAdvisorChain streamAdvisorChain) {
        return null;
    }

    @NotNull
    @Override
    public String getName() {
        return "";
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
