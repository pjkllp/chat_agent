package org.example.travel_agent.advisor;

import lombok.RequiredArgsConstructor;
import org.example.travel_agent.dao.entity.AiChatMemoryEntity;
import org.example.travel_agent.common.MessageConvertUtil;
import org.example.travel_agent.store.Store;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@Component("contextMemoryAdvisor")
@RequiredArgsConstructor
public class ContextMemoryAdvisor implements CallAdvisor, StreamAdvisor {

    private final Store redisStore;

    @Value("${memory.history.len:10}")
    public int MAX_HISTORY;

    @NotNull
    @Override
    public ChatClientResponse adviseCall(@NotNull ChatClientRequest chatClientRequest, @NotNull CallAdvisorChain callAdvisorChain) {
        String conversationId = (String) chatClientRequest.context().get("conversationId");
        long userId = (long)chatClientRequest.context().get("userId");
        if (conversationId == null || conversationId.isBlank()) {
            return callAdvisorChain.nextCall(chatClientRequest);
        }

        List<AiChatMemoryEntity> records = redisStore.load(userId,conversationId,MAX_HISTORY);
        List<Message> historyMessages = MessageConvertUtil.toMessages(records);
        if (historyMessages.isEmpty()) {
            return callAdvisorChain.nextCall(chatClientRequest);
        }

        ArrayList<Message> promptMessages = new ArrayList<>();
        promptMessages.addAll(historyMessages);
        promptMessages.addAll(chatClientRequest.prompt().getInstructions());
        ChatClientRequest newRequest = chatClientRequest.copy().mutate()
                .prompt(new Prompt(promptMessages, chatClientRequest.prompt().getOptions()))
                .build();
        return callAdvisorChain.nextCall(newRequest);
    }

    @NotNull
    @Override
    public Flux<ChatClientResponse> adviseStream(@NotNull ChatClientRequest chatClientRequest, @NotNull StreamAdvisorChain streamAdvisorChain) {
        String conversationId = (String) chatClientRequest.context().get("conversationId");
        if (conversationId == null || conversationId.isBlank()) {
            return streamAdvisorChain.nextStream(chatClientRequest);
        }
        long userId = (long)chatClientRequest.context().get("userId");
        List<AiChatMemoryEntity> records = redisStore.load(userId,conversationId,MAX_HISTORY);
        List<Message> historyMessages = MessageConvertUtil.toMessages(records);
        if (historyMessages.isEmpty()) {
            return streamAdvisorChain.nextStream(chatClientRequest);
        }

        ArrayList<Message> promptMessages = new ArrayList<>();
        promptMessages.addAll(historyMessages);
        promptMessages.addAll(chatClientRequest.prompt().getInstructions());
        ChatClientRequest newRequest = chatClientRequest.copy().mutate()
                .prompt(new Prompt(promptMessages, chatClientRequest.prompt().getOptions()))
                .build();
        return streamAdvisorChain.nextStream(newRequest);
    }

    @NotNull
    @Override
    public String getName() {
        return "context-memory-advisor";
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
