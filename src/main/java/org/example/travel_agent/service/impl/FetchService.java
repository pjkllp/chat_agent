package org.example.travel_agent.service.impl;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.example.travel_agent.common.JinaUtil;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class FetchService {

    private final JinaUtil jinaUtil;

    public FetchService(JinaUtil jinaUtil) {
        this.jinaUtil = jinaUtil;
    }

    @Value("${app.fetch.max-content-length:3000}")
    private int maxContentLength;

    @Value("${app.fetch.timeout-ms:5000}")
    private int timeoutMs;

    public String fetchContent(String url) {
        if (StrUtil.isBlank(url)) {
            return "";
        }
        String content = "";
        try {
            content = jinaUtil.parseUrlToMarkdown(url);
            if (StrUtil.isNotBlank(content)) {
                log.info("Fetch success via Jina, url={}", url);
            }
            return truncate(content);
        } catch (Exception e) {
            log.warn("Jina fetch failed, url={}, msg={}", url, e.getMessage());
        }

        try {
            Document document = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .followRedirects(true)
                    .ignoreContentType(true)
                    .maxBodySize(0)
                    .timeout(timeoutMs)
                    .get();
            content = document.body() == null ? "" : document.body().text();
            if (StrUtil.isNotBlank(content)) {
                log.info("Fetch success via Jsoup fallback, url={}", url);
            }
            return truncate(content);
        } catch (Exception e) {
            log.warn("Jsoup fallback failed, url={}, msg={}", url, e.getMessage());
            return "";
        }
    }

    private String truncate(String content) {
        if (StrUtil.isBlank(content)) {
            return "";
        }
        if (content.length() > maxContentLength) {
            return content.substring(0, maxContentLength);
        }
        return content;
    }
}
