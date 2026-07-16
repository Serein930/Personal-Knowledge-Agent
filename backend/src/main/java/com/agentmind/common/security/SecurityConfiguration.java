package com.agentmind.common.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;

/**
 * 无状态 API 安全配置。
 *
 * <p>本地模式使用 HMAC 签发 JWT；OIDC 模式根据发行方元数据校验外部令牌。
 * 两种生产模式都不创建服务端会话，并统一关闭表单登录和 HTTP Basic。</p>
 */
@Configuration
public class SecurityConfiguration {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AgentMindSecurityProperties properties,
            RestSecurityErrorHandler errorHandler
    ) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable());
        if (properties.getMode() == SecurityMode.DISABLED) {
            http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
        } else {
            http.authorizeHttpRequests(authorize -> authorize
                            .requestMatchers("/actuator/health", "/actuator/info", "/api/v1/auth/**").permitAll()
                            .anyRequest().authenticated())
                    .oauth2ResourceServer(resourceServer -> resourceServer
                            .jwt(Customizer.withDefaults())
                            .authenticationEntryPoint(errorHandler))
                    .exceptionHandling(exceptions -> exceptions
                            .authenticationEntryPoint(errorHandler)
                            .accessDeniedHandler(errorHandler));
        }
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    @ConditionalOnProperty(prefix = "agentmind.security", name = "mode", havingValue = "local-jwt")
    public JwtEncoder localJwtEncoder(AgentMindSecurityProperties properties) {
        SecretKey key = localSecretKey(properties);
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    @ConditionalOnProperty(prefix = "agentmind.security", name = "mode", havingValue = "local-jwt")
    public JwtDecoder localJwtDecoder(AgentMindSecurityProperties properties) {
        return NimbusJwtDecoder.withSecretKey(localSecretKey(properties)).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "agentmind.security", name = "mode", havingValue = "oidc")
    public JwtDecoder oidcJwtDecoder(AgentMindSecurityProperties properties) {
        if (!StringUtils.hasText(properties.getIssuerUri())) {
            throw new IllegalStateException("OIDC 安全模式必须配置 AGENTMIND_OIDC_ISSUER_URI");
        }
        return JwtDecoders.fromIssuerLocation(properties.getIssuerUri());
    }

    private SecretKey localSecretKey(AgentMindSecurityProperties properties) {
        if (!StringUtils.hasText(properties.getJwtSecret()) || properties.getJwtSecret().length() < 32) {
            throw new IllegalStateException("本地 JWT 模式必须配置至少 32 个字符的 AGENTMIND_JWT_SECRET");
        }
        return new SecretKeySpec(properties.getJwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
