package com.agentmind.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 本地前后端分离联调的跨域配置。
 *
 * <p>允许来源通过配置注入，默认只开放 Vite 本地开发地址，不使用任意来源通配符。</p>
 */
@Configuration
public class WebCorsConfiguration implements WebMvcConfigurer {

    private final String allowedOrigin;

    public WebCorsConfiguration(
            @Value("${agentmind.web.allowed-origin:http://localhost:5173}") String allowedOrigin
    ) {
        this.allowedOrigin = allowedOrigin;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigin)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", "Accept", "Authorization")
                .maxAge(3600);
    }
}
