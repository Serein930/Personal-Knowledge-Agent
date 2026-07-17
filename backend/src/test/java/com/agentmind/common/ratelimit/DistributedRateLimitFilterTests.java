package com.agentmind.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** 验证接口分类、JWT 用户隔离、拒绝响应和 Redis 故障策略。 */
class DistributedRateLimitFilterTests {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatedRagRequestShouldUseUserQuota() throws Exception {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setRagRequests(5);
        AtomicReference<RateLimitScope> capturedScope = new AtomicReference<>();
        AtomicReference<String> capturedSubject = new AtomicReference<>();
        DistributedRateLimiter limiter = (scope, subject, limit) -> {
            capturedScope.set(scope);
            capturedSubject.set(subject);
            return new RateLimitDecision(true, limit, 4, 0);
        };
        DistributedRateLimitFilter filter = filter(limiter, properties);
        SecurityContextHolder.getContext().setAuthentication(jwtAuthentication(7L));

        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/v1/workspaces/1/rag/chat");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(capturedScope.get()).isEqualTo(RateLimitScope.RAG);
        assertThat(capturedSubject.get()).isEqualTo("user:7");
        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("5");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("4");
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void exceededAuthenticationQuotaShouldReturnUnified429() throws Exception {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setAuthenticationRequests(2);
        DistributedRateLimiter limiter = (scope, subject, limit) ->
                new RateLimitDecision(false, limit, 0, 17);
        DistributedRateLimitFilter filter = filter(limiter, properties);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr("192.0.2.10");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("17");
        assertThat(response.getContentAsString()).contains("RATE_LIMITED");
    }

    @Test
    void redisFailureShouldFailClosedByDefault() throws Exception {
        RateLimitProperties properties = new RateLimitProperties();
        DistributedRateLimiter limiter = (scope, subject, limit) -> {
            throw new IllegalStateException("Redis 连接失败");
        };
        DistributedRateLimitFilter filter = filter(limiter, properties);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/users/me"),
                response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("DEPENDENCY_UNAVAILABLE");
    }

    @Test
    void downstreamBusinessExceptionShouldNotBeMisreportedAsRedisFailure() {
        RateLimitProperties properties = new RateLimitProperties();
        DistributedRateLimiter limiter = (scope, subject, limit) ->
                new RateLimitDecision(true, limit, limit - 1L, 0);
        DistributedRateLimitFilter filter = filter(limiter, properties);

        assertThatThrownBy(() -> filter.doFilter(
                new MockHttpServletRequest("GET", "/api/v1/users/me"),
                new MockHttpServletResponse(),
                (request, response) -> {
                    throw new IllegalStateException("模拟业务异常");
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("模拟业务异常");
    }

    private DistributedRateLimitFilter filter(
            DistributedRateLimiter limiter,
            RateLimitProperties properties
    ) {
        return new DistributedRateLimitFilter(
                limiter, properties, new ObjectMapper().findAndRegisterModules(), new SimpleMeterRegistry());
    }

    private JwtAuthenticationToken jwtAuthentication(Long userId) {
        Instant now = Instant.now();
        Jwt jwt = new Jwt(
                "test-token",
                now,
                now.plusSeconds(300),
                Map.of("alg", "none"),
                Map.of("sub", userId.toString(), "uid", userId)
        );
        return new JwtAuthenticationToken(jwt);
    }
}
