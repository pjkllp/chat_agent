package org.example.travel_agent.config;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.*;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class LLMConfig {

    private final NodeAction rewrite_node;

    private final NodeAction intent_identify_node;

    private final NodeAction search_node;

    private final NodeAction retrieve_node;

    private final NodeAction fetch_node;

    private final NodeAction summary_node;

    private final NodeAction answer_node;

    @Bean
    public CompiledGraph deepThinkGraph() throws GraphStateException {
        KeyStrategyFactory strategyFactory=()->{
            return Map.of("input",new ReplaceStrategy());
        };
        StateGraph deepThinkGraph = new StateGraph("deepThinkGraph",strategyFactory);

        deepThinkGraph.addNode("rewrite_node", AsyncNodeAction.node_async(
                rewrite_node
        )).addNode("intent_identify_node",AsyncNodeAction.node_async(
                intent_identify_node
        )).addNode("search_node",AsyncNodeAction.node_async(
                search_node
        )).addNode("retrieve_node",AsyncNodeAction.node_async(
                retrieve_node
        )).addNode("fetch_node",AsyncNodeAction.node_async(
                fetch_node
        )).addNode("summary_node",AsyncNodeAction.node_async(
                summary_node
        )).addNode("answer_node",AsyncNodeAction.node_async(
                answer_node
                //先进入重写节点根据历史对话重写内容
        )).addEdge(StateGraph.START,"rewrite_node")
                .addEdge("rewrite_node","intent_identify_node")
                //工具节点归并
                .addEdge("search_node","fetch_node")
                .addEdge("retrieve_node","summary_node")
                //答案输出
                .addEdge("summary_node","answer_node")
                .addEdge("answer_node",StateGraph.END)
                //条件路由
                .addConditionalEdges("intent_identify_node", AsyncCommandAction.node_async(
                        (state, config) ->{
                            String searchIntent = state.value("search_intent", "");
                            String retrieveIntent = state.value("retrieve_intent", "");
                            boolean hasSearchIntent = searchIntent != null && !searchIntent.isBlank();
                            boolean hasRetrieveIntent = retrieveIntent != null && !retrieveIntent.isBlank();
                            // 两种意图都存在时，先走搜索链路，抓取后再进入知识库检索
                            if (hasSearchIntent && hasRetrieveIntent) {
                                return new Command("both_intent");
                            }
                            if (hasSearchIntent){
                                return new Command("search_intent");
                            }
                            if (hasRetrieveIntent){
                                return new Command("retrieve_intent");
                            }
                            log.info("没有使用工具的意图，大模型直接回答");
                            return new Command("none");
                        }),
                        Map.of(
                                "both_intent","search_node",
                                "search_intent","search_node",
                                "retrieve_intent","retrieve_node",
                                "none","answer_node"
                        )
                )
                .addConditionalEdges("fetch_node", AsyncCommandAction.node_async(
                        (state, config) ->{
                            String retrieveIntent = state.value("retrieve_intent", "");
                            boolean hasRetrieveIntent = retrieveIntent != null && !retrieveIntent.isBlank();
                            if (hasRetrieveIntent) {
                                return new Command("to_retrieve");
                            }
                            return new Command("to_summary");
                        }),
                        Map.of(
                                "to_retrieve","retrieve_node",
                                "to_summary","summary_node"
                        )
                );
        return deepThinkGraph.compile();
    }
}
