package org.example.travel_agent.common;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.travel_agent.Exceptions.ServerException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class JinaUtil {

    private final RestClient restClient;

    @Value("${jina.api.key:}")
    private String jinaApiKey;

    @Value("${jina.api.url:https://r.jina.ai}")
    private String baseUrl;

    public String parseUrlToMarkdown(String url){
        if (StrUtil.isBlank(url)) {
            return "";
        }
        String reqUrl = baseUrl.endsWith("/") ? baseUrl + url : baseUrl + "/" + url;
        try {
            return restClient.get()
                    .uri(reqUrl)
                    // 配置请求头：指定AI引擎、返回格式、鉴权
                    .header("X-Engine", "readerlm-v2") // 用最新的AI提取引擎，效果更好
                    .header("X-Return-Format", "markdown") // 固定返回Markdown
                    .headers(headers -> {
                        // 有API Key就添加鉴权，提升请求限额
                        if (jinaApiKey != null && !jinaApiKey.isBlank()) {
                            headers.add("Authorization", "Bearer " + jinaApiKey);
                        }
                    })
                    // 执行请求，获取字符串结果
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.warn("Jina parse failed, url={}, msg={}", url, e.getMessage());
            throw new ServerException("网页解析失败");
        }
    }
}
