package org.example.travel_agent.Node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.RequiredArgsConstructor;
import org.example.travel_agent.Exceptions.RepeatToManyException;
import org.example.travel_agent.common.SseEventUtil;
import org.example.travel_agent.dto.IntentDTO;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class intent_identify_node implements NodeAction {

    private final ChatClient deepThinkChatClient;

    private final Advisor memoryAdvisor;

    private final int MAX_REPEAT_COUNT=3;

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        SseEventUtil.sendNodeStatus(state, "intent_identify_node", "start", "开始识别用户意图");


        ClassPathResource classPathResource = new ClassPathResource("prompt/intent_identify_node.st");

        String repeatQuestion = state.value("repeat_question", "");

        IntentDTO intentDTO = null;
        int count=0;

        while (intentDTO==null&&count<MAX_REPEAT_COUNT){
            intentDTO = deepThinkChatClient.prompt()
                    .advisors(memoryAdvisor)
                    .system(classPathResource)
                    .user(repeatQuestion)
                    .call().entity(IntentDTO.class);
            if (count>=3){
                throw new RepeatToManyException("模型重试生成次数过多");
            }
            count++;
        }

        SseEventUtil.sendNodeStatus(state, "intent_identify_node", "finish", "意图识别完成");

        return Map.of(
                "search_intent",intentDTO.getSearchIntent(),
                "retrieve_intent",intentDTO.getRetrieveIntent(),
                "tool_intent",intentDTO.getToolIntent()
        );
    }
}
