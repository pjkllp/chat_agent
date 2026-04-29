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
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component("persistMemoryAdvisor")
@RequiredArgsConstructor
public class PersistMemoryAdvisor implements CallAdvisor, StreamAdvisor {

    private final Store redisStore;

    @Value("${memory.history.len:10}")
    public int MAX_HISTORY;

    @NotNull
    @Override
    public ChatClientResponse adviseCall(@NotNull ChatClientRequest chatClientRequest, @NotNull CallAdvisorChain callAdvisorChain) {
        String conversationId = (String) chatClientRequest.context().get("conversationId");
        long userId =(long) chatClientRequest.context().get("userId");
        if (conversationId == null || conversationId.isBlank()) {
            return callAdvisorChain.nextCall(chatClientRequest);
        }

        List<UserMessage> userMessages = chatClientRequest.prompt().getUserMessages();

        ChatClientRequest requestWithHistory = appendHistoryToRequest(chatClientRequest,userId,conversationId);

        ChatClientResponse chatClientResponse = callAdvisorChain.nextCall(requestWithHistory);

        //等大模型消息回来之后连同用户消息和大模型消息一起存进redis

        List<AssistantMessage> assistantMessages = Objects.requireNonNull(chatClientResponse.chatResponse())
                .getResults()
                .stream()
                .map(Generation::getOutput)
                .toList();

        ArrayList<Message> messages = new ArrayList<>();
        messages.addAll(userMessages);
        messages.addAll(assistantMessages);
        redisStore.append(MessageConvertUtil.toEntities(messages, userId, conversationId),userId,conversationId);
        return chatClientResponse;
    }

    @NotNull
    @Override
    public Flux<ChatClientResponse> adviseStream(@NotNull ChatClientRequest chatClientRequest, @NotNull StreamAdvisorChain streamAdvisorChain) {
        String conversationId = (String) chatClientRequest.context().get("conversationId");
        Long userId = chatClientRequest.context().get("userId") instanceof Long v ? v : null;
        if (conversationId == null || conversationId.isBlank()) {
            return streamAdvisorChain.nextStream(chatClientRequest);
        }

        ChatClientRequest requestWithHistory = appendHistoryToRequest(chatClientRequest,userId,conversationId);
        List<UserMessage> userMessages = requestWithHistory.prompt().getUserMessages();
        StringBuilder assistantBuffer = new StringBuilder();

        return streamAdvisorChain.nextStream(requestWithHistory)
                .doOnNext(response -> {
                    List<Generation> results = response.chatResponse() == null ? List.of() : response.chatResponse().getResults();
                    for (Generation result : results) {
                        AssistantMessage output = result.getOutput();
                        if (output != null && output.getText() != null) {
                            assistantBuffer.append(output.getText());
                        }
                    }
                })
                .doOnComplete(() -> {
                    ArrayList<Message> messages = new ArrayList<>();
                    messages.addAll(userMessages);
                    if (!assistantBuffer.isEmpty()) {
                        messages.add(new AssistantMessage(assistantBuffer.toString()));
                    }
                    redisStore.append(MessageConvertUtil.toEntities(messages, userId, conversationId),userId,conversationId);
                });
    }

    @NotNull
    @Override
    public String getName() {
        return "persist-memory-advisor";
    }

    @Override
    public int getOrder() {
        return 0;
    }

    private ChatClientRequest appendHistoryToRequest(ChatClientRequest chatClientRequest,long userId,String conversationId) {
        List<AiChatMemoryEntity> records = redisStore.load(userId,conversationId,MAX_HISTORY);
        List<Message> historyMessages = MessageConvertUtil.toMessages(records);
        if (historyMessages.isEmpty()) {
            return chatClientRequest;
        }
        ArrayList<Message> promptMessages = new ArrayList<>();
        promptMessages.addAll(historyMessages);
        promptMessages.addAll(chatClientRequest.prompt().getInstructions());
        return chatClientRequest.copy().mutate()
                .prompt(new Prompt(promptMessages, chatClientRequest.prompt().getOptions()))
                .build();
    }
}
