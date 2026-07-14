package com.agentmind.agent.confirmation.config;

import jakarta.validation.constraints.AssertTrue;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 写工具确认流程配置。
 *
 * <p>确认令牌只在较短时间内有效，避免用户离开页面后旧确认单仍可长期执行写操作。</p>
 */
@Component
@Validated
@ConfigurationProperties(prefix = "agentmind.agent.write-confirmation")
public class AgentWriteToolConfirmationProperties {

    private Duration ttl = Duration.ofMinutes(5);

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        this.ttl = ttl;
    }

    @AssertTrue(message = "写工具确认令牌有效期必须大于零")
    public boolean isTtlValid() {
        return ttl != null && !ttl.isZero() && !ttl.isNegative();
    }
}
