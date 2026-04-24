package org.example.travel_agent.Node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.travel_agent.advisor.PersistMemoryAdvisor;
import org.example.travel_agent.common.SseEventUtil;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class answer_node implements NodeAction {

    private final ChatClient deepThinkChatClient;

    private final PersistMemoryAdvisor persistMemoryAdvisor;

    private final SseEventUtil sseEventUtil;

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        if (!sseEventUtil.hasEmitter(state)) {
            log.warn("SSE emitter is missing, skip answer streaming");
            return Map.of();
        }

        sseEventUtil.sendNodeStatus(state, "answer_node", "start", "开始生成最终答案");

        String summaryPrompt = state.value("summary_prompt", "");
        if (summaryPrompt == null || summaryPrompt.isBlank()) {
            summaryPrompt = state.value("rewrite_question", state.value("original_question", ""));
        }

        ClassPathResource classPathResource = new ClassPathResource("prompt/answer.st");

        String conversationId = state.value("conversationId", "");

        Long userId = state.value("userId", Long.class).orElse(null);

        try {
            AtomicBoolean hasStreamChunk = new AtomicBoolean(false);
            deepThinkChatClient.prompt()
                    .advisors(persistMemoryAdvisor)
                    .advisors(advisorSpec -> advisorSpec.params(
                            Map.of(
                                    "conversationId", conversationId,
                                    "userId", userId
                            )
                    ))
                    .system(classPathResource)
                    .user(summaryPrompt)
                    .stream()
                    .content()
                    .doOnNext(data -> {
                        hasStreamChunk.set(true);
                        try {
                            sseEventUtil.sendAnswerChunk(state, data);
                        } catch (Exception e) {
                            throw new IllegalStateException("Failed to send answer chunk via SSE", e);
                        }
                    })
                    .blockLast();

            if (!hasStreamChunk.get()) {
                log.warn("answer_node stream produced no chunk, fallback to call().content()");
                String fullAnswer = deepThinkChatClient.prompt()
                        .advisors(persistMemoryAdvisor)
                        .advisors(advisorSpec -> advisorSpec.params(
                                Map.of(
                                        "conversationId", conversationId,
                                        "userId", userId
                                )
                        ))
                        .system(classPathResource)
                        .user(summaryPrompt)
                        .call()
                        .content();
                if (fullAnswer != null && !fullAnswer.isBlank()) {
                    sseEventUtil.sendAnswerChunk(state, fullAnswer);
                }
            }

            sseEventUtil.sendNodeStatus(state, "answer_node", "finish", "最终答案已生成");
            sseEventUtil.sendNodeStatus(state, "answer_node", "done", "工作流执行完成");
        } catch (Exception e) {
            log.error("Generate answer failed", e);
            sseEventUtil.sendNodeStatus(state, "answer_node", "error", "生成答案失败");
        } finally {
            sseEventUtil.complete(state);
        }

        return Map.of();
    }
}
