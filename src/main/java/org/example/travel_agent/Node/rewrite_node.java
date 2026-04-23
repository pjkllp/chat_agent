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

import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class rewrite_node implements NodeAction {

    private final ChatClient deepThinkChatClient;

    private final Advisor memoryAdvisor;

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {

        ClassPathResource classPathResource = new ClassPathResource("prompt/rewrite.st");

        String originalQuestion = state.value("original_question", "");

        log.info("开始改写用户问题，用户原始问题:{}",originalQuestion);

        SseEventUtil.sendNodeStatus(state, "rewrite_node", "start", "开始改写用户问题");

        ChatClient.CallResponseSpec call = deepThinkChatClient.prompt()
                .system(classPathResource)
                .advisors(memoryAdvisor)
                .call();

        String rewriteQuestion = Objects.requireNonNull(call.content()).isBlank()?originalQuestion:call.content();

        log.info("改写用户问题完毕，改写后的问题:{}",rewriteQuestion);

        SseEventUtil.sendNodeStatus(state, "rewrite_node", "finish", "问题改写完成");

        return Map.of("rewrite_question",rewriteQuestion);
    }
}
