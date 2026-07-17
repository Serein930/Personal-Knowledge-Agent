package com.agentmind.common.security.proxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/** 将可信代理校验注册到所有安全过滤器之前。 */
@Configuration
@EnableConfigurationProperties(TrustedProxyProperties.class)
public class TrustedProxyConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "agentmind.web.trusted-proxy", name = "enabled", havingValue = "true")
    public FilterRegistrationBean<TrustedProxyHeaderFilter> trustedProxyHeaderFilter(
            TrustedProxyProperties properties,
            ObjectMapper objectMapper
    ) {
        FilterRegistrationBean<TrustedProxyHeaderFilter> registration =
                new FilterRegistrationBean<>(new TrustedProxyHeaderFilter(properties, objectMapper));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
