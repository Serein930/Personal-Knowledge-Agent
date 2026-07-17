package com.agentmind.common.security.proxy;

import com.agentmind.common.exception.ErrorCode;
import com.agentmind.common.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 在认证和限流之前验证反向代理转发头。
 *
 * <p>应用不会盲目信任客户端自带的 {@code X-Forwarded-*}。只有连接来源位于配置的网关 CIDR，
 * 并且网关明确声明外部协议为 HTTPS 时才继续处理。没有任何转发头的容器内健康检查不受影响。</p>
 */
public class TrustedProxyHeaderFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TrustedProxyHeaderFilter.class);
    private static final List<String> FORWARDED_HEADERS = List.of(
            "Forwarded", "X-Forwarded-For", "X-Forwarded-Proto",
            "X-Forwarded-Host", "X-Forwarded-Port", "X-Real-IP"
    );

    private final TrustedProxyProperties properties;
    private final IpCidrMatcher cidrMatcher;
    private final ObjectMapper objectMapper;

    public TrustedProxyHeaderFilter(TrustedProxyProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.cidrMatcher = new IpCidrMatcher(properties.getCidrs());
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!containsForwardedHeader(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!cidrMatcher.matches(request.getRemoteAddr())) {
            log.warn("拒绝非可信来源携带转发头：来源地址={}", request.getRemoteAddr());
            writeFailure(response, "请求携带了未经可信网关清洗的转发头");
            return;
        }
        if (properties.isRequireHttps() && !isForwardedHttps(request)) {
            log.warn("拒绝可信代理转发的非 HTTPS 请求：来源地址={}", request.getRemoteAddr());
            writeFailure(response, "生产请求必须通过 HTTPS 网关进入");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean containsForwardedHeader(HttpServletRequest request) {
        return FORWARDED_HEADERS.stream().anyMatch(header -> request.getHeader(header) != null);
    }

    private boolean isForwardedHttps(HttpServletRequest request) {
        String protocol = request.getHeader("X-Forwarded-Proto");
        if (protocol == null) {
            return false;
        }
        return "https".equals(protocol.split(",", 2)[0].trim().toLowerCase(Locale.ROOT));
    }

    private void writeFailure(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.failure(ErrorCode.UNTRUSTED_PROXY.code(), message));
    }
}
