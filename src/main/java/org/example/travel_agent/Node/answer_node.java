package org.example.travel_agent.Node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.travel_agent.common.SseEventUtil;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class answer_node implements NodeAction {

    private final ChatClient deepThinkChatClient;

    private final Advisor memoryAdvisor;

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        SseEmitter sse = state.value("sse", SseEmitter.class).orElse(null);
        if (sse == null) {
            log.warn("SSE emitter is missing, skip answer streaming");
            return Map.of();
        }

        SseEventUtil.sendNodeStatus(state, "answer_node", "start", "开始生成最终答案");

        String summaryPrompt = state.value("summary_prompt", "");
        if (summaryPrompt == null || summaryPrompt.isBlank()) {
            summaryPrompt = state.value("rewrite_question", state.value("original_question", ""));
        }

        ClassPathResource classPathResource = new ClassPathResource("prompt/answer.st");

        String conversationId = state.value("conversationId", "");

        ChatClient.StreamResponseSpec stream = deepThinkChatClient.prompt()
                .advisors(memoryAdvisor)
                .advisors(advisorSpec -> advisorSpec.param("conversationId",conversationId))
                .system(classPathResource)
                .user(summaryPrompt)
                .stream();

        try {
            stream.content()
                    .doOnNext(data -> {
                        try {
                            sse.send(SseEmitter.event().name("answer").data(data));
                        } catch (IOException e) {
                            throw new IllegalStateException("Failed to send answer chunk via SSE", e);
                        }
                    })
                    .doOnComplete(() -> {
                        try {
                            SseEventUtil.sendNodeStatus(state, "answer_node", "finish", "最终答案已生成");
                            SseEventUtil.sendNodeStatus(state, "answer_node", "done", "工作流执行完成");
                        } catch (IOException e) {
                            throw new IllegalStateException("Failed to send finish event via SSE", e);
                        }
                    })
                    .blockLast();
        } catch (Exception e) {
            log.error("Generate answer failed", e);
            SseEventUtil.sendNodeStatus(state, "answer_node", "error", "生成答案失败");
        } finally {
            SseEventUtil.complete(state);
        }

        return Map.of();
    }
}
