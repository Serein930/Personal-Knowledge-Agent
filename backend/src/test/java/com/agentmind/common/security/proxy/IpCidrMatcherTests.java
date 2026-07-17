package com.agentmind.common.security.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.validation.Validation;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 验证 IPv4、IPv6 和非法网段配置的边界行为。 */
class IpCidrMatcherTests {

    @Test
    void shouldMatchConfiguredIpv4AndIpv6Networks() {
        IpCidrMatcher matcher = new IpCidrMatcher(List.of("10.20.0.0/16", "2001:db8::/32"));

        assertThat(matcher.matches("10.20.30.40")).isTrue();
        assertThat(matcher.matches("10.21.30.40")).isFalse();
        assertThat(matcher.matches("2001:db8::8")).isTrue();
        assertThat(matcher.matches("2001:db9::8")).isFalse();
    }

    @Test
    void shouldRejectHostnameAndInvalidPrefix() {
        assertThatThrownBy(() -> new IpCidrMatcher(List.of("gateway.internal/24")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IpCidrMatcher(List.of("10.0.0.0/40")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void propertiesShouldRejectUniversalTrustedNetwork() {
        TrustedProxyProperties properties = new TrustedProxyProperties();
        properties.setEnabled(true);
        properties.setCidrs(List.of("0.0.0.0/0"));

        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            assertThat(validatorFactory.getValidator().validate(properties))
                    .extracting("message")
                    .contains("可信代理网段不能覆盖全部 IPv4 或 IPv6 地址");
        }
    }
}
