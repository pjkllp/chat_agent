package org.example.travel_agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /**
     * HS256 密钥，长度建议 ≥ 32 字节（256 bit）。
     */
    private String secret = "change-me-in-production-use-at-least-32-chars!!";

    /**
     * Access Token 有效期（毫秒），默认 24 小时。
     */
    private long expirationMs = 86_400_000L;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public void setExpirationMs(long expirationMs) {
        this.expirationMs = expirationMs;
    }
}
