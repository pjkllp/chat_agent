package org.example.travel_agent.Node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.RequiredArgsConstructor;
import org.example.travel_agent.common.SseEventUtil;
import org.example.travel_agent.dao.entity.KnowledgeVectorEntity;
import org.example.travel_agent.dao.mapper.KnowledgeVectorMapper;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class retrieve_node implements NodeAction {

    private final EmbeddingModel embeddingModel;

    private final KnowledgeVectorMapper knowledgeVectorMapper;
    private final SseEventUtil sseEventUtil;

    @Value("${app.retrieval.top-k:3}")
    private int topK;

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        long startMs = System.currentTimeMillis();
        sseEventUtil.sendNodeStatus(state, "retrieve_node", "start", "开始召回知识库内容");

        String query = state.value("retrieve_intent", "");
        query = query == null ? "" : query.trim();
        if (query.isBlank()) {
            sseEventUtil.sendNodeStatus(state, "retrieve_node", "finish", "retrieve_intent 为空，跳过召回");
            return Map.of("retrieve_context", "", "retrieve_matches", List.of());
        }

        float[] queryEmbedding = embeddingModel.embed(query);
        if (queryEmbedding == null || queryEmbedding.length == 0) {
            sseEventUtil.sendNodeStatus(state, "retrieve_node", "finish", "向量生成失败，跳过召回");
            return Map.of("retrieve_context", "", "retrieve_matches", List.of());
        }

        String queryVector = toVectorLiteral(queryEmbedding);
        List<KnowledgeVectorEntity> entities = knowledgeVectorMapper.similaritySearch(queryVector, Math.max(1, topK));

        List<String> fragments = new ArrayList<>();
        List<Map<String, Object>> matches = new ArrayList<>();
        for (KnowledgeVectorEntity entity : entities) {
            String content = entity.getContent() == null ? "" : entity.getContent().trim();
            if (!content.isEmpty()) {
                fragments.add(content);
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", entity.getId());
            row.put("content", entity.getContent() == null ? "" : entity.getContent());
            row.put("distance", entity.getDistance() == null ? 0D : entity.getDistance());
            matches.add(row);
        }
        String retrieveContext = fragments.stream().collect(Collectors.joining("\n\n"));

        long costMs = System.currentTimeMillis() - startMs;
        sseEventUtil.sendNodeStatus(
                state,
                "retrieve_node",
                "finish",
                "知识库召回完成，命中 " + matches.size() + " 条，耗时 " + costMs + "ms"
        );

        return Map.of("retrieve_context", retrieveContext, "retrieve_matches", matches);
    }

    private String toVectorLiteral(float[] vector) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(vector[i]);
        }
        builder.append(']');
        return builder.toString();
    }
}
