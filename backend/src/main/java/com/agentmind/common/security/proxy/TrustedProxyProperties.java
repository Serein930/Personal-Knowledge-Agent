package com.agentmind.common.security.proxy;

import jakarta.validation.constraints.AssertTrue;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 可信反向代理网段和外部传输协议校验配置。 */
@Validated
@ConfigurationProperties(prefix = "agentmind.web.trusted-proxy")
public class TrustedProxyProperties {

    private boolean enabled;
    private boolean requireHttps = true;
    private List<String> cidrs = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isRequireHttps() {
        return requireHttps;
    }

    public void setRequireHttps(boolean requireHttps) {
        this.requireHttps = requireHttps;
    }

    public List<String> getCidrs() {
        return cidrs;
    }

    public void setCidrs(List<String> cidrs) {
        this.cidrs = cidrs == null ? new ArrayList<>() : new ArrayList<>(cidrs);
    }

    @AssertTrue(message = "启用可信代理校验时必须配置至少一个代理网段")
    public boolean isCidrConfigurationValid() {
        return !enabled || (cidrs != null && cidrs.stream().anyMatch(value -> value != null && !value.isBlank()));
    }

    @AssertTrue(message = "可信代理网段不能覆盖全部 IPv4 或 IPv6 地址")
    public boolean isUniversalNetworkRejected() {
        if (!enabled || cidrs == null) {
            return true;
        }
        return cidrs.stream()
                .filter(value -> value != null)
                .map(String::trim)
                .noneMatch(value -> "0.0.0.0/0".equals(value) || "::/0".equals(value));
    }
}
