package org.example.travel_agent.Node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.travel_agent.Exceptions.RepeatToManyException;
import org.example.travel_agent.advisor.ContextMemoryAdvisor;
import org.example.travel_agent.common.SseEventUtil;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class intent_identify_node implements NodeAction {

    private final ChatClient deepThinkChatClient;

    private final ContextMemoryAdvisor memoryAdvisor;
    private final SseEventUtil sseEventUtil;

    private final int MAX_REPEAT_COUNT=3;

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        sseEventUtil.sendNodeStatus(state, "intent_identify_node", "start", "开始识别用户意图");

        ClassPathResource classPathResource = new ClassPathResource("./prompt/intent_identify_node.st");
        String rewriteQuestion = state.value("rewrite_question", state.value("original_question", ""));
        Long userId = state.value("userId", Long.class).orElse(null);
        Map<String, Object> result = new HashMap<>();
        result.put("search_intent", "");
        result.put("retrieve_intent", "");

        String conversationId = state.value("conversationId", "");

        boolean parsed = false;
        int count = 0;
        while (!parsed && count < MAX_REPEAT_COUNT) {
            try {
                String content = deepThinkChatClient.prompt()
                        .advisors(memoryAdvisor)
                        .advisors(advisorSpec -> advisorSpec.params(
                                Map.of(
                                        "conversationId",conversationId,
                                        "userId",userId
                                ))
                        )
                        .system(classPathResource)
                        .user(rewriteQuestion)
                        .call()
                        .content();
                content = content == null ? "" : content.trim();
                if (content.isBlank()) {
                    count++;
                    continue;
                }

                JSONObject jsonObject = JSON.parseObject(content);
                if (jsonObject == null) {
                    count++;
                    continue;
                }

                Object intentsObj = jsonObject.get("intents");
                List<Map<String, String>> intentPairs = normalizeIntentPairs(intentsObj);
                for (Map<String, String> pair : intentPairs) {
                    for (Map.Entry<String, String> entry : pair.entrySet()) {
                        String intentKey = entry.getKey();
                        String intentValue = entry.getValue();
                        if (intentKey == null || intentKey.isBlank()) {
                            continue;
                        }
                        if (!result.containsKey(intentKey)) {
                            continue;
                        }
                        result.put(intentKey, intentValue == null ? "" : intentValue.trim());
                    }
                }
                parsed = true;
            } catch (Exception parseException) {
                log.warn("意图解析失败，准备重试: {}", parseException.getMessage());
                count++;
            }
        }
        if (!parsed) {
            throw new RepeatToManyException("模型重试生成次数过多，未生成合法JSON");
        }

        sseEventUtil.sendNodeStatus(state, "intent_identify_node", "finish", "意图识别完成");
        return result;
    }

    private List<Map<String, String>> normalizeIntentPairs(Object intentsObj) {
        List<Map<String, String>> pairs = new ArrayList<>();
        if (intentsObj instanceof JSONObject intentsJsonObject) {
            Map<String, String> item = new HashMap<>();
            for (String key : intentsJsonObject.keySet()) {
                item.put(key, intentsJsonObject.getString(key));
            }
            pairs.add(item);
            return pairs;
        }
        if (intentsObj instanceof JSONArray intentsArray) {
            for (int i = 0; i < intentsArray.size(); i++) {
                Object itemObj = intentsArray.get(i);
                if (itemObj instanceof JSONObject itemJson) {
                    Map<String, String> item = new HashMap<>();
                    for (String key : itemJson.keySet()) {
                        item.put(key, itemJson.getString(key));
                    }
                    if (!item.isEmpty()) {
                        pairs.add(item);
                    }
                }
            }
        }
        return pairs;
    }
}
