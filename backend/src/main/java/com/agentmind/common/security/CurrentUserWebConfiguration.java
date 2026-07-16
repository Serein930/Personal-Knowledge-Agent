package com.agentmind.common.security;

import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 注册控制器当前用户参数解析器。 */
@Configuration
@EnableConfigurationProperties(AgentMindSecurityProperties.class)
public class CurrentUserWebConfiguration implements WebMvcConfigurer {

    private final AgentMindSecurityProperties properties;

    public CurrentUserWebConfiguration(AgentMindSecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentUserIdArgumentResolver(properties));
    }
}
