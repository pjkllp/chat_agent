package org.example.travel_agent.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.travel_agent.common.UserContext;
import org.example.travel_agent.dto.UserInfo;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 管理员拦截器。必须注册在 {@link AuthInterceptor} 之后，
 * 依赖其写入 UserContext 的用户信息。
 */
@Component
public class AdminInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        UserInfo userInfo = UserContext.get();
        if (userInfo == null || !Integer.valueOf(1).equals(userInfo.getIsAdmin())) {
            writeForbidden(response);
            return false;
        }
        return true;
    }

    private void writeForbidden(HttpServletResponse response) throws Exception {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"message\":\"该功能仅限管理员访问\"}");
    }
}
