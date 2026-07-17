package com.agentmind.common.security.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** 验证转发头不能绕过可信代理和 HTTPS 边界。 */
class TrustedProxyHeaderFilterTests {

    @Test
    void trustedHttpsProxyShouldPass() throws Exception {
        MockHttpServletRequest request = forwardedRequest("10.20.30.40", "https");
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] invoked = {false};
        FilterChain chain = (servletRequest, servletResponse) -> invoked[0] = true;

        filter().doFilter(request, response, chain);

        assertThat(invoked[0]).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void untrustedSourceShouldBeRejected() throws Exception {
        MockHttpServletRequest request = forwardedRequest("203.0.113.9", "https");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, (servletRequest, servletResponse) -> { });

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("UNTRUSTED_PROXY");
    }

    @Test
    void nonHttpsForwardingShouldBeRejected() throws Exception {
        MockHttpServletRequest request = forwardedRequest("10.20.30.40", "http");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, (servletRequest, servletResponse) -> { });

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("必须通过 HTTPS 网关");
    }

    @Test
    void directHealthProbeWithoutForwardedHeadersShouldPass() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] invoked = {false};

        filter().doFilter(request, response, (servletRequest, servletResponse) -> invoked[0] = true);

        assertThat(invoked[0]).isTrue();
    }

    private TrustedProxyHeaderFilter filter() {
        TrustedProxyProperties properties = new TrustedProxyProperties();
        properties.setEnabled(true);
        properties.setRequireHttps(true);
        properties.setCidrs(java.util.List.of("10.0.0.0/8", "2001:db8::/32"));
        // 测试映射器与 Spring Boot 运行时保持一致，注册 OffsetDateTime 所需模块。
        return new TrustedProxyHeaderFilter(properties, new ObjectMapper().findAndRegisterModules());
    }

    private MockHttpServletRequest forwardedRequest(String remoteAddress, String protocol) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/workspaces");
        request.setRemoteAddr(remoteAddress);
        request.addHeader("X-Forwarded-For", "198.51.100.7");
        request.addHeader("X-Forwarded-Proto", protocol);
        return request;
    }
}
