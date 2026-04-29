package org.example.travel_agent.Node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.travel_agent.advisor.ContextMemoryAdvisor;
import org.example.travel_agent.common.SseEventUtil;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class rewrite_node implements NodeAction {

    private final ChatClient deepThinkChatClient;

    private final ContextMemoryAdvisor memoryAdvisor;
    private final SseEventUtil sseEventUtil;


    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {

        ClassPathResource classPathResource = new ClassPathResource("prompt/rewrite.st");

        String originalQuestion = state.value("original_question", "");

        Long userId = state.value("userId", Long.class).orElse(null);

        log.info("开始改写用户问题，用户原始问题:{}",originalQuestion);

        sseEventUtil.sendNodeStatus(state, "rewrite_node", "start", "开始改写用户问题");

        String conversationId = state.value("conversationId", "");

        String rewriteQuestion = deepThinkChatClient.prompt()
                .system(classPathResource)
                .user(originalQuestion)
                .advisors(memoryAdvisor)
                .advisors(advisorSpec -> advisorSpec.params(
                        Map.of(
                                "conversationId", conversationId,
                                "userId", userId
                        ))
                ).call().content();

        log.info("改写用户问题完毕，改写后的问题:{}",rewriteQuestion);

        sseEventUtil.sendNodeStatus(state, "rewrite_node", "finish", "问题改写完成");

        return Map.of("rewrite_question",rewriteQuestion);
    }
}
