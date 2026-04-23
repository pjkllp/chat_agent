package org.example.travel_agent.Node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.RequiredArgsConstructor;
import org.example.travel_agent.common.SseEventUtil;
import org.example.travel_agent.dto.BaiduSearchResult;
import org.example.travel_agent.service.BaiduSearchService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class search_node implements NodeAction {

    private final BaiduSearchService baiduSearchService;

    @Value("${app.search.top-k:5}")
    private int topK;

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        long startMs = System.currentTimeMillis();
        SseEventUtil.sendNodeStatus(state, "search_node", "start", "开始调用搜索能力");

        String query = state.value("search_intent", "");
        query = query == null ? "" : query.trim();
        if (query == null || query.isBlank()) {
            SseEventUtil.sendNodeStatus(state, "search_node", "finish", "search_intent 为空，跳过联网检索");
            return Map.of("search_context", "", "search_matches", List.of());
        }

        List<BaiduSearchResult> results = baiduSearchService.search(query, topK);
        List<Map<String, Object>> matches = new ArrayList<>();
        for (BaiduSearchResult result : results) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("title", result.getTitle() == null ? "" : result.getTitle());
            row.put("url", result.getUrl() == null ? "" : result.getUrl());
            row.put("snippet", result.getSnippet() == null ? "" : result.getSnippet());
            matches.add(row);
        }
        String searchContext = results.stream()
                .map(item -> "标题: " + item.getTitle() + "\n链接: " + item.getUrl())
                .collect(Collectors.joining("\n\n"));

        long costMs = System.currentTimeMillis() - startMs;
        SseEventUtil.sendNodeStatus(
                state,
                "search_node",
                "finish",
                "百度搜索完成，命中 " + results.size() + " 条，耗时 " + costMs + "ms"
        );
        return Map.of("search_context", searchContext, "search_matches", matches);
    }
}
