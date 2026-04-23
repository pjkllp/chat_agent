package org.example.travel_agent.config;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.*;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.util.ArrayList;
import java.util.Map;

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
    public ChatClient repeatClient(ChatModel chatModel){
        ClassPathResource classPathResource = new ClassPathResource("prompt/system.st");
        return ChatClient.builder(chatModel)
                .defaultSystem(classPathResource)
                .defaultOptions(
                        ChatOptions.builder()
                                .temperature(0.1)
                                .build()
                ).build();
    }

    @Bean
    public CompiledGraph deepThinkGraph() throws GraphStateException {
        KeyStrategyFactory strategyFactory=()->{
            return Map.of("input",new ReplaceStrategy());
        };
        StateGraph deepThinkGraph = new StateGraph("deepThinkGraph",strategyFactory);

        deepThinkGraph.addNode("repeat_node", AsyncNodeAction.node_async(
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
        )).addEdge(StateGraph.START,"repeat_node")
                .addEdge("repeat_node","intent_identify_node")
                //工具节点归并
                .addEdge("search_node","fetch_node")

                .addEdge("fetch_node","summary_node")
                .addEdge("retrieve_node","summary_node")
                //答案输出
                .addEdge("summary_node","answer_node")
                .addEdge("answer_node",StateGraph.END)
                //条件路由
                .addParallelConditionalEdges("intent_identify_node", AsyncMultiCommandAction.node_async(
                        (state, config) ->{
                            String searchIntent = state.value("search_intent", "");
                            String retrieveIntent = state.value("retrieve_intent", "");
                            ArrayList<String> routes=new ArrayList<>();
                            boolean hasSearchIntent = searchIntent != null && !searchIntent.isBlank();
                            boolean hasRetrieveIntent = retrieveIntent != null && !retrieveIntent.isBlank();
                            //判断有哪些意图
                            if (hasSearchIntent || hasRetrieveIntent){
                                if(hasSearchIntent){
                                    routes.add("search_intent");
                                }
                                if (hasRetrieveIntent){
                                    routes.add("retrieve_intent");
                                }
                            }else {
                                //没有意图直接进入answer_node直接回答
                                routes.add("none");
                            }

                            return new MultiCommand(routes);
                        }),
                        Map.of(
                                "search_intent","search_node",
                                "retrieve_intent","retrieve_node",
                                "none","answer_node"
                        )
                );
        return deepThinkGraph.compile();
    }
}
