package org.example.travel_agent.config;

import org.example.travel_agent.interceptor.AdminInterceptor;
import org.example.travel_agent.interceptor.AuthInterceptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    private final AdminInterceptor adminInterceptor;

    public WebMvcConfig(AuthInterceptor authInterceptor, AdminInterceptor adminInterceptor) {
        this.authInterceptor = authInterceptor;
        this.adminInterceptor = adminInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 登录态校验：先执行，UserContext 由它写入。
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/**",
                        "/error"
                );

        // 管理员校验：依赖上一步写入的 UserContext，因此顺序不可颠倒。
        registry.addInterceptor(adminInterceptor)
                .addPathPatterns(
                        "/api/admin/**",
                        "/api/knowledge/**"
                );
    }
}
