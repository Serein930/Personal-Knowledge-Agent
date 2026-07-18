package com.agentmind.common.security;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 阻止真实认证与内存用户仓储组合，避免重启后账号和成员关系丢失。 */
@Component
public class ProductionSecurityConfigurationValidator {

    private final AgentMindSecurityProperties properties;
    private final String coreStore;
    private final boolean allowMemoryForTesting;

    public ProductionSecurityConfigurationValidator(
            AgentMindSecurityProperties properties,
            @Value("${agentmind.core.persistence.store:memory}") String coreStore,
            @Value("${agentmind.security.allow-memory-for-testing:false}") boolean allowMemoryForTesting
    ) {
        this.properties = properties;
        this.coreStore = coreStore;
        this.allowMemoryForTesting = allowMemoryForTesting;
    }

    @PostConstruct
    public void validate() {
        if (properties.getMode() != SecurityMode.DISABLED
                && !"jdbc".equalsIgnoreCase(coreStore)
                && !allowMemoryForTesting) {
            throw new IllegalStateException("启用生产身份认证时，核心数据仓储必须配置为 jdbc");
        }
    }
}
