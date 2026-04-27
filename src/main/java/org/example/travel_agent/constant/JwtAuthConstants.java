package org.example.travel_agent.constant;

public final class JwtAuthConstants {

    private JwtAuthConstants() {
    }

    /**
     * 拦截器校验通过后写入 {@link jakarta.servlet.http.HttpServletRequest#setAttribute(String, Object)}，
     * 业务接口应优先读取该值，避免信任客户端传来的 userId。
     */
    public static final String REQUEST_ATTR_USER_ID = "jwtSubjectUserId";
}
