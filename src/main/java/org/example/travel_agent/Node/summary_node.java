package org.example.travel_agent.Node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.travel_agent.common.SseEventUtil;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class summary_node implements NodeAction {

    private final SseEventUtil sseEventUtil;

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        sseEventUtil.sendNodeStatus(state, "summary_node", "start", "开始汇总工具结果");

        String rewriteQuestion = state.value("rewrite_question", "");
        String searchContext = state.value("search_context", "");
        String retrieveContext = state.value("retrieve_context", "");
        String searchKeyword = state.value("search_intent", "");
        String retrieveKeyword = state.value("retrieve_intent", "");

        ClassPathResource classPathResource = new ClassPathResource("prompt/summary.st");

        PromptTemplate promptTemplate = new PromptTemplate(classPathResource);
        String originalQuestion = state.value("original_question", "");



        String summaryInput = promptTemplate.render(Map.of(
                "original_question",originalQuestion,
                "rewrite_question", rewriteQuestion == null ? "" : rewriteQuestion,
                "search_keyword", searchKeyword == null ? "" : searchKeyword,
                "search_context", searchContext == null ? "" : searchContext,
                "retrieve_keyword", retrieveKeyword == null ? "" : retrieveKeyword,
                "retrieve_context", retrieveContext == null ? "" : retrieveContext
        ));
        log.info(summaryInput);
        sseEventUtil.sendNodeStatus(state, "summary_node", "finish", "结果汇总完成");
        return Map.of("summary_prompt",summaryInput);
    }
}
