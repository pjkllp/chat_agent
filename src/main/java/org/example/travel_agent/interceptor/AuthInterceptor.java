package org.example.travel_agent.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.example.travel_agent.Exceptions.ClientException;
import org.example.travel_agent.common.JwtUtil;
import org.example.travel_agent.common.UserContext;
import org.example.travel_agent.constant.JwtAuthConstants;
import org.example.travel_agent.dto.UserInfo;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private static final String BEARER_SCHEME = "Bearer";

    private final JwtUtil jwtUtil;

    private final StringRedisTemplate stringRedisTemplate;

    public static final String USER_AUTH_KEY="user:login:%s";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String rawAuth = request.getHeader("Authorization");
        if (rawAuth == null || rawAuth.isBlank()) {
            writeUnauthorized(response, "缺少 Authorization 请求头，格式必须为：Bearer <access_token>");
            return false;
        }
        if (!rawAuth.startsWith(BEARER_SCHEME + " ")) {
            writeUnauthorized(response, "Authorization 请求头格式错误，必须为：Bearer <access_token>");
            return false;
        }
        String token = rawAuth.substring((BEARER_SCHEME + " ").length()).trim();
        if (token.isBlank()) {
            writeUnauthorized(response, "access_token 不能为空");
            return false;
        }

        UserInfo userInfo = jwtUtil.parseUserInfo(token);

        String redisToken = stringRedisTemplate.opsForValue().get(String.format(USER_AUTH_KEY,userInfo.getUsername()));

        if (verifyAuthParams(userInfo)&&!Objects.equals(token,redisToken)){
            throw new ClientException("用户身份非法");
        }

        UserContext.set(userInfo);

        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable ModelAndView modelAndView) throws Exception {
        UserContext.remove();
    }

    private boolean verifyAuthParams(UserInfo userInfo){
        String username = userInfo.getUsername();

        String email = userInfo.getEmail();

        Integer isAdmin = userInfo.getIsAdmin();

        if (username==null||username.isBlank()||
                email==null||email.isBlank()||
                isAdmin==null
        ){
            return false;
        }
        return true;
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"message\":\"" + escapeJson(message) + "\"}");
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
