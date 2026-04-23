package org.example.travel_agent.Node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.RequiredArgsConstructor;
import org.example.travel_agent.common.SseEventUtil;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class summary_node implements NodeAction {

    private final ChatClient deepThinkChatClient;

    private final Advisor memoryAdvisor;

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        SseEventUtil.sendNodeStatus(state, "summary_node", "start", "开始汇总工具结果");

        String repeatQuestion = state.value("repeat_question", "");
        String searchContext = state.value("search_context", "");
        String retrieveContext = state.value("retrieve_context", "");

        ClassPathResource classPathResource = new ClassPathResource("prompt/summary.st");
        String summaryInput = """
                用户问题:
                %s

                联网检索结果:
                %s

                知识库召回结果:
                %s
                """.formatted(repeatQuestion, searchContext, retrieveContext);

        ChatClient.CallResponseSpec call = deepThinkChatClient.prompt()
                .advisors(memoryAdvisor)
                .system(classPathResource)
                .user(summaryInput)
                .call();

        String content = call.content();

        SseEventUtil.sendNodeStatus(state, "summary_node", "finish", "结果汇总完成");
        return Map.of("summary_prompt",content);
    }
}
