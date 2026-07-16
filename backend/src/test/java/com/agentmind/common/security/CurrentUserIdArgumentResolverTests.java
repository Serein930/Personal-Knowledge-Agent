package com.agentmind.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentmind.common.exception.BusinessException;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** 验证用户编号只能来自可信认证上下文。 */
class CurrentUserIdArgumentResolverTests {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void disabledModeShouldUseSeedUserForTests() throws Exception {
        AgentMindSecurityProperties properties = new AgentMindSecurityProperties();
        CurrentUserIdArgumentResolver resolver = new CurrentUserIdArgumentResolver(properties);

        assertThat(resolver.resolveArgument(parameter(), null, null, null)).isEqualTo(1L);
    }

    @Test
    void jwtModeShouldReadUidClaim() throws Exception {
        AgentMindSecurityProperties properties = new AgentMindSecurityProperties();
        properties.setMode(SecurityMode.LOCAL_JWT);
        CurrentUserIdArgumentResolver resolver = new CurrentUserIdArgumentResolver(properties);
        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "HS256"), Map.of("sub", "ignored", "uid", 42L));
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt, java.util.List.of(), "42");
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThat(resolver.resolveArgument(parameter(), null, null, null)).isEqualTo(42L);
    }

    @Test
    void invalidIdentityClaimShouldBeRejected() throws Exception {
        AgentMindSecurityProperties properties = new AgentMindSecurityProperties();
        properties.setMode(SecurityMode.OIDC);
        CurrentUserIdArgumentResolver resolver = new CurrentUserIdArgumentResolver(properties);
        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "RS256"), Map.of("sub", "external-user"));
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(
                jwt, java.util.List.of(), "external-user");
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThatThrownBy(() -> resolver.resolveArgument(parameter(), null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户编号");
    }

    private MethodParameter parameter() throws NoSuchMethodException {
        return new MethodParameter(CurrentUserIdArgumentResolverTests.class
                .getDeclaredMethod("controllerParameter", Long.class), 0);
    }

    @SuppressWarnings("unused")
    private void controllerParameter(@CurrentUserId Long userId) {
    }
}
