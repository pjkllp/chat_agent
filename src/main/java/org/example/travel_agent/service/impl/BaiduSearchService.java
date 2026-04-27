package org.example.travel_agent.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.http.HttpUtil;
import cn.hutool.http.Method;
import cn.hutool.http.useragent.UserAgentUtil;
import cn.hutool.http.useragent.UserAgent;
import lombok.extern.slf4j.Slf4j;
import org.example.travel_agent.dto.BaiduSearchResult;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class BaiduSearchService {

    @Value("${app.search.timeout-ms:8000}")
    private int timeoutMs;

    public List<BaiduSearchResult> search(String query, int topK) {
        if (StrUtil.isBlank(query)) {
            return List.of();
        }

        int limit = Math.max(1, topK);
        String optimizedQuery = optimizeQuery(query);

        List<BaiduSearchResult> baidu = filterByQuery(searchFromBaidu(optimizedQuery, limit), optimizedQuery, false);
        if (!baidu.isEmpty()) {
            return baidu;
        }
        log.info("Baidu returned 0 result, fallback to Bing, query={}", optimizedQuery);
        return filterByQuery(searchFromBing(optimizedQuery, limit), optimizedQuery, true);
    }

    private List<BaiduSearchResult> searchFromBaidu(String query, int topK) {
        String httpsUrl = "https://www.baidu.com/s?wd=" + HttpUtil.encodeParams(query, StandardCharsets.UTF_8) + "&rn=" + topK + "&ie=utf-8";
        String httpUrl = "http://www.baidu.com/s?wd=" + HttpUtil.encodeParams(query, StandardCharsets.UTF_8) + "&rn=" + topK + "&ie=utf-8";

        HttpRequest request = HttpRequest.of(httpsUrl)
                .method(Method.GET)
                .timeout(timeoutMs)
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("Referer", "https://www.baidu.com/")
                .header("User-Agent", defaultUserAgent());

        try (HttpResponse response = request.execute()) {
            String body = response.body();
            String contentType = StrUtil.nullToEmpty(response.header("Content-Type"));
            String setCookie = StrUtil.nullToEmpty(response.header("Set-Cookie"));
            String bodyPreview = body == null ? "" : body.substring(0, Math.min(500, body.length())).replaceAll("\\s+", " ");
            log.info("Baidu response status={}, contentType={}, setCookiePresent={}, bodyPreview={}",
                    response.getStatus(),
                    contentType,
                    !setCookie.isBlank(),
                    bodyPreview);
            if (response.getStatus() != HttpStatus.HTTP_OK) {
                log.warn("Baidu search non-200 status: {}", response.getStatus());
                return List.of();
            }
            if (body != null && body.contains("location.replace") && body.contains("http://www.baidu.com")) {
                log.info("Baidu returned https->http redirect shell, retry with http endpoint");
                try (HttpResponse retryResp = HttpRequest.of(httpUrl)
                        .method(Method.GET)
                        .timeout(timeoutMs)
                        .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                        .header("Referer", "http://www.baidu.com/")
                        .header("User-Agent", defaultUserAgent())
                        .execute()) {
                    if (retryResp.getStatus() == HttpStatus.HTTP_OK) {
                        body = retryResp.body();
                    }
                } catch (Exception retryEx) {
                    log.warn("Baidu http retry failed: {}", retryEx.getMessage());
                }
            }

            List<BaiduSearchResult> parsed = parseHtml(body, topK);
            log.info("Baidu parse done, query={}, parsedCount={}", query, parsed.size());
            return parsed;
        } catch (Exception e) {
            log.warn("Baidu search failed: {}", e.getMessage());
            return List.of();
        }
    }

    private List<BaiduSearchResult> searchFromBing(String query, int topK) {
        String url = "https://cn.bing.com/search?q=" + HttpUtil.encodeParams(query, StandardCharsets.UTF_8) + "&count=" + topK;
        HttpRequest request = HttpRequest.of(url)
                .method(Method.GET)
                .timeout(Math.min(timeoutMs, 5000))
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("User-Agent", defaultUserAgent());
        try (HttpResponse response = request.execute()) {
            String body = response.body();
            String bodyPreview = body == null ? "" : body.substring(0, Math.min(300, body.length())).replaceAll("\\s+", " ");
            log.info("Bing response status={}, bodyPreview={}", response.getStatus(), bodyPreview);
            if (response.getStatus() != HttpStatus.HTTP_OK) {
                return List.of();
            }
            List<BaiduSearchResult> parsed = parseBingHtml(body, topK);
            log.info("Bing parse done, query={}, parsedCount={}", query, parsed.size());
            return parsed;
        } catch (Exception e) {
            log.warn("Bing fallback failed: {}", e.getMessage());
            return List.of();
        }
    }

    private List<BaiduSearchResult> parseHtml(String html, int topK) {
        if (StrUtil.isBlank(html)) {
            return List.of();
        }

        List<BaiduSearchResult> results = new ArrayList<>();
        Document doc = Jsoup.parse(html);
        Elements cards = doc.select("#content_left .result, #content_left .result-op");
        log.info("Baidu parse selector count={}, topK={}", cards.size(), topK);
        for (Element card : cards) {
            if (results.size() >= topK) {
                break;
            }
            Element linkEl = card.selectFirst("h3 a[href]");
            if (linkEl == null) {
                continue;
            }
            String title = cleanText(linkEl.text());
            String rawUrl = StrUtil.nullToEmpty(linkEl.attr("href")).trim();
            String finalUrl = resolveRealUrl(rawUrl);
            String snippet = extractSnippet(card);
            if (StrUtil.isBlank(title) || StrUtil.isBlank(finalUrl)) {
                continue;
            }
            results.add(new BaiduSearchResult(title, finalUrl, snippet));
        }
        return results;
    }

    private String extractSnippet(Element card) {
        Element snippetEl = card.selectFirst(".c-abstract, .content-right_8Zs40, .c-span-last");
        if (snippetEl == null) {
            snippetEl = card.selectFirst("div");
        }
        if (snippetEl == null) {
            return "";
        }
        return cleanText(snippetEl.text());
    }

    private List<BaiduSearchResult> parseBingHtml(String html, int topK) {
        if (StrUtil.isBlank(html)) {
            return List.of();
        }
        List<BaiduSearchResult> results = new ArrayList<>();
        Document doc = Jsoup.parse(html);
        Elements cards = doc.select("#b_results li.b_algo");
        log.info("Bing parse selector count={}, topK={}", cards.size(), topK);
        for (Element card : cards) {
            if (results.size() >= topK) {
                break;
            }
            Element a = card.selectFirst("h2 a[href]");
            if (a == null) {
                continue;
            }
            String title = cleanText(a.text());
            String url = StrUtil.nullToEmpty(a.attr("href")).trim();
            String snippet = cleanText(card.select("p").text());
            if (StrUtil.isBlank(title) || StrUtil.isBlank(url)) {
                continue;
            }
            results.add(new BaiduSearchResult(title, url, snippet));
        }
        return results;
    }

    private String optimizeQuery(String query) {
        // 仅做轻量清洗，不做重排/打分，保持搜索引擎原始排序
        String q = cleanText(query)
                .replaceAll("[\\u3000\\t\\r\\n]+", " ")
                .replaceAll("[“”\"'`]+", "")
                .trim();
        if (q.isBlank()) {
            return q;
        }
        return q;
    }

    private List<BaiduSearchResult> filterByQuery(List<BaiduSearchResult> results, String query, boolean fallbackOriginalWhenEmpty) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        Set<String> keywords = extractKeywords(query);
        if (keywords.isEmpty()) {
            return results;
        }
        List<BaiduSearchResult> filtered = new ArrayList<>();
        for (BaiduSearchResult item : results) {
            String text = (cleanText(item.getTitle()) + " " + cleanText(item.getSnippet())).toLowerCase();
            boolean matched = false;
            for (String kw : keywords) {
                if (text.contains(kw.toLowerCase())) {
                    matched = true;
                    break;
                }
            }
            if (matched) {
                filtered.add(item);
            }
        }
        if (filtered.isEmpty() && fallbackOriginalWhenEmpty) {
            return results;
        }
        return filtered;
    }

    private Set<String> extractKeywords(String query) {
        String normalized = cleanText(query)
                .replaceAll("(?i)请问|是谁|是什么|为什么|怎么样|怎么|哪些|哪个|哪里|吗|呢|呀|啊", " ")
                .replaceAll("[^\\p{IsHan}A-Za-z0-9\\s]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        Set<String> keywords = new LinkedHashSet<>();
        if (normalized.isBlank()) {
            return keywords;
        }
        Matcher matcher = Pattern.compile("[\\p{IsHan}A-Za-z0-9]{2,}").matcher(normalized);
        while (matcher.find()) {
            keywords.add(matcher.group());
        }
        return keywords;
    }

    private String cleanText(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    /**
     * 百度很多结果是中转链接，这里快速尝试解析最终真实地址，失败则返回原始 URL。
     */
    private String resolveRealUrl(String rawUrl) {
        if (StrUtil.isBlank(rawUrl)) {
            return "";
        }
        String url = rawUrl.trim();
        if (!(url.startsWith("http://") || url.startsWith("https://"))) {
            return url;
        }
        // 只有百度中转链接才做二次解析，避免每条结果都额外发请求拖慢速度
        if (!(url.contains("www.baidu.com/link?") || url.contains("www.baidu.com/from=") || url.contains("m.baidu.com/link?"))) {
            return url;
        }
        try (HttpResponse response = HttpRequest.of(url)
                .method(Method.GET)
                .timeout(Math.min(timeoutMs, 1200))
                .setFollowRedirects(false)
                .header("User-Agent", defaultUserAgent())
                .execute()) {
            String location = response.header("Location");
            if (StrUtil.isNotBlank(location)) {
                return location.trim();
            }
        } catch (Exception e) {
            log.debug("Resolve redirect failed, url={}, msg={}", url, e.getMessage());
        }
        return url;
    }

    private String defaultUserAgent() {
        UserAgent userAgent = UserAgentUtil.parse("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
        return userAgent == null ? "Mozilla/5.0" : userAgent.toString();
    }
}
