package org.example.travel_agent.common;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import lombok.extern.slf4j.Slf4j;
import org.example.travel_agent.Exceptions.ServerException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class JinaUtil {

    @Value("${jina.api.key:}")
    private String jinaApiKey;

    @Value("${jina.api.url:https://r.jina.ai}")
    private String baseUrl;

    @Value("${jina.timeout-ms:6000}")
    private int timeoutMs;

    public String parseUrlToMarkdown(String url){
        if (StrUtil.isBlank(url)) {
            return "";
        }
        String target = url.trim();
        String reqUrl = baseUrl.endsWith("/") ? baseUrl + target : baseUrl + "/" + target;
        try {
            HttpRequest request = HttpRequest.of(reqUrl)
                    .timeout(timeoutMs)
                    .header("X-Engine", "readerlm-v2")
                    .header("X-Return-Format", "markdown");
            if (jinaApiKey != null && !jinaApiKey.isBlank()) {
                request.header("Authorization", "Bearer " + jinaApiKey);
            }
            try (HttpResponse response = request.execute()) {
                return StrUtil.nullToEmpty(response.body());
            }
        } catch (Exception e) {
            log.warn("Jina parse failed, reqUrl={}, msg={}", reqUrl, e.getMessage());
            throw new ServerException("网页解析失败");
        }
    }
}
