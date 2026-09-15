package org.example.travel_agent.advisor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.travel_agent.dao.entity.AiChatMemoryEntity;
import org.example.travel_agent.common.MessageConvertUtil;
import org.example.travel_agent.memory.LLMMemory;
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
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component("persistMemoryAdvisor")
@RequiredArgsConstructor
public class PersistMemoryAdvisor implements CallAdvisor, StreamAdvisor {

    private final LLMMemory llmMemory;

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
        llmMemory.save(MessageConvertUtil.toEntities(messages, userId, conversationId, messageId(chatClientRequest)),
                userId, conversationId);
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
        StringBuilder assistantBuffer = new StringBuilder();
        AtomicBoolean persisted = new AtomicBoolean(false);

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
                // doOnComplete 只在正常完成时触发，用户点停止（CancelException 让流变成 onError）
                // 或模型报错时整轮都不会落库，用户提问会从历史里凭空消失。
                // doFinally 在 complete/error/cancel 下都执行，CAS 保证只落一次。
                .doFinally(signal -> {
                    if (!persisted.compareAndSet(false, true)) {
                        return;
                    }
                    // 取 chatClientRequest 而非 requestWithHistory：后者被前置了历史，
                    // 用它会把历史里的用户消息重复写一遍。
                    ArrayList<Message> messages = new ArrayList<>(chatClientRequest.prompt().getUserMessages());
                    if (!assistantBuffer.isEmpty()) {
                        messages.add(new AssistantMessage(assistantBuffer.toString()));
                    }
                    llmMemory.save(MessageConvertUtil.toEntities(messages, userId, conversationId, messageId(chatClientRequest)),
                            userId, conversationId);
                });
    }

    /** 取出本轮对话标识；不存在时返回 null，由存储层自行生成 id。 */
    private Long messageId(ChatClientRequest request) {
        Object value = request.context().get("messageId");
        return value instanceof Long v ? v : null;
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
        List<AiChatMemoryEntity> records = llmMemory.getMemory(userId, conversationId, MAX_HISTORY);
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
