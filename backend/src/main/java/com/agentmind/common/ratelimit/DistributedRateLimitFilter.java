package com.agentmind.common.ratelimit;

import com.agentmind.common.exception.ErrorCode;
import com.agentmind.common.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * API 分布式限流过滤器。
 *
 * <p>已认证请求按用户共享配额，登录和注册等匿名请求按客户端地址共享配额。只有在部署入口已经
 * 清洗并覆盖转发地址请求头时，才应配置 {@code client-ip-header}，否则使用连接来源地址以避免伪造。</p>
 */
public class DistributedRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(DistributedRateLimitFilter.class);

    private final DistributedRateLimiter rateLimiter;
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public DistributedRateLimitFilter(
            DistributedRateLimiter rateLimiter,
            RateLimitProperties properties,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry
    ) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        RateLimitScope scope = resolveScope(request);
        int limit = properties.limitFor(scope);
        RateLimitDecision decision;
        try {
            decision = rateLimiter.tryAcquire(scope, resolveSubject(request), limit);
        } catch (RuntimeException exception) {
            record(scope, "dependency_error");
            log.error("Redis 分布式限流执行失败：接口分类={}", scope.key(), exception);
            if (properties.isFailOpen()) {
                filterChain.doFilter(request, response);
                return;
            }
            writeFailure(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    ErrorCode.DEPENDENCY_UNAVAILABLE, "请求配额服务暂时不可用");
            return;
        }

        response.setHeader("X-RateLimit-Limit", Integer.toString(decision.limit()));
        response.setHeader("X-RateLimit-Remaining", Long.toString(decision.remaining()));
        if (!decision.allowed()) {
            record(scope, "rejected");
            response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
            writeFailure(response, org.springframework.http.HttpStatus.TOO_MANY_REQUESTS.value(),
                    ErrorCode.RATE_LIMITED, "请求过于频繁，请稍后重试");
            return;
        }
        record(scope, "allowed");
        filterChain.doFilter(request, response);
    }

    private RateLimitScope resolveScope(HttpServletRequest request) {
        String path = request.getRequestURI().toLowerCase(Locale.ROOT);
        if (path.startsWith("/api/v1/auth/")) {
            return RateLimitScope.AUTHENTICATION;
        }
        if ("POST".equalsIgnoreCase(request.getMethod())
                && path.contains("/documents/")
                && (path.endsWith("/files") || path.endsWith("/web-pages"))) {
            return RateLimitScope.INGESTION;
        }
        if (path.contains("/rag/") || path.endsWith("/knowledge/search")) {
            return RateLimitScope.RAG;
        }
        return RateLimitScope.GENERAL;
    }

    private String resolveSubject(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            Object userId = jwtAuthentication.getToken().getClaim("uid");
            return "user:" + (userId == null ? authentication.getName() : userId);
        }
        String configuredHeader = properties.getClientIpHeader();
        if (configuredHeader != null && !configuredHeader.isBlank()) {
            String forwardedAddress = request.getHeader(configuredHeader);
            if (forwardedAddress != null && !forwardedAddress.isBlank()) {
                return "ip:" + forwardedAddress.split(",", 2)[0].trim();
            }
        }
        return "ip:" + request.getRemoteAddr();
    }

    private void record(RateLimitScope scope, String result) {
        meterRegistry.counter("agentmind.http.rate_limit.requests",
                "scope", scope.key(), "result", result).increment();
    }

    private void writeFailure(HttpServletResponse response, int status, ErrorCode errorCode, String message)
            throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.failure(errorCode.code(), message));
    }
}
