package org.example.travel_agent.Node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.travel_agent.common.SseEventUtil;
import org.example.travel_agent.service.impl.FetchService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class fetch_node implements NodeAction {

    private final FetchService fetchService;
    private final SseEventUtil sseEventUtil;
    @Qualifier("chatAsyncExecutor")
    private final Executor chatAsyncExecutor;

    @Value("${app.fetch.top-k:15}")
    private int topK;

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        long startMs = System.currentTimeMillis();
        log.info("[fetch_node] enter, topK={}", topK);
        sseEventUtil.sendNodeStatus(state, "fetch_node", "start", "开始抓取搜索结果正文");

        List<Map<String, Object>> searchMatches = state.value("search_matches", List.of());
        log.info("[fetch_node] search_matches size={}", searchMatches == null ? 0 : searchMatches.size());
        if (searchMatches == null || searchMatches.isEmpty()) {
            log.info("[fetch_node] no search matches, skip fetch");
            sseEventUtil.sendNodeStatus(state, "fetch_node", "finish", "无可抓取链接，跳过正文抓取");
            return Map.of("fetched_docs", List.of(), "search_context", state.value("search_context", ""));
        }

        List<Map<String, Object>> fetchedDocs;
        int limit = Math.max(1, topK);
        int actualSize = Math.min(limit, searchMatches.size());
        List<CompletableFuture<Map<String, Object>>> futures = new ArrayList<>();
        for (int i = 0; i < actualSize; i++) {
            final int idx = i + 1;
            final Map<String, Object> match = searchMatches.get(i);
            futures.add(CompletableFuture.supplyAsync(() -> {
                String title = String.valueOf(match.getOrDefault("title", ""));
                String url = String.valueOf(match.getOrDefault("url", ""));
                String snippet = String.valueOf(match.getOrDefault("snippet", ""));
                try {
                    sseEventUtil.sendNodeStatus(
                            state,
                            "fetch_node",
                            "progress",
                            "抓取第 " + idx + "/" + actualSize + " 条: " + (title.isBlank() ? url : title)
                    );
                } catch (IOException e) {
                    log.info("解析失败");
                }
                log.info("[fetch_node] start item {}/{}, title={}, url={}", idx, actualSize, title, url);

                String content = "";
                long itemStart = System.currentTimeMillis();
                try {
                    if (url != null && !url.isBlank()) {
                        content = fetchService.fetchContent(url);
                    }
                } catch (Exception ex) {
                    log.warn("[fetch_node] fetch item failed, url={}, msg={}", url, ex.getMessage());
                }
                if (content == null || content.isBlank()) {
                    content = snippet == null ? "" : snippet;
                }
                log.info("[fetch_node] finish item {}/{}, cost={}ms, contentLength={}", idx, actualSize, System.currentTimeMillis() - itemStart, content.length());

                Map<String, Object> doc = new LinkedHashMap<>();
                doc.put("idx", idx);
                doc.put("title", title);
                doc.put("url", url);
                doc.put("snippet", snippet);
                doc.put("content", content);
                return doc;
            }, chatAsyncExecutor));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        fetchedDocs = futures.stream()
                .map(CompletableFuture::join)
                .sorted(Comparator.comparingInt(doc -> Integer.parseInt(String.valueOf(doc.getOrDefault("idx", "0")))))
                .peek(doc -> doc.remove("idx"))
                .collect(Collectors.toList());

        String fetchedContext = fetchedDocs.stream()
                .map(doc -> "标题: " + doc.getOrDefault("title", "")
                        + "\n链接: " + doc.getOrDefault("url", "")
                        + "\n摘要: " + doc.getOrDefault("snippet", "")
                        + "\n正文: " + doc.getOrDefault("content", ""))
                .collect(Collectors.joining("\n\n---\n\n"));

        long costMs = System.currentTimeMillis() - startMs;
        log.info("[fetch_node] finish, fetchedDocs={}, cost={}ms", fetchedDocs.size(), costMs);
        sseEventUtil.sendNodeStatus(
                state,
                "fetch_node",
                "finish",
                "网页正文抓取完成，处理 " + fetchedDocs.size() + " 条，耗时 " + costMs + "ms"
        );

        return Map.of("fetched_docs", fetchedDocs, "search_context", fetchedContext);
    }
}
