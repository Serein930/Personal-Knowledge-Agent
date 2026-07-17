package com.agentmind.common.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** 验证正式环境不会以关闭认证、内存仓储或示例秘密启动。 */
class ProductionConfigurationValidatorTests {

    @Test
    void completeProductionConfigurationShouldPass() {
        MockEnvironment environment = validEnvironment();

        assertThatCode(() -> new ProductionConfigurationValidator(environment).afterSingletonsInstantiated())
                .doesNotThrowAnyException();
    }

    @Test
    void insecureConfigurationShouldFailWithoutLeakingSecret() {
        MockEnvironment environment = validEnvironment()
                .withProperty("agentmind.security.mode", "disabled")
                .withProperty("agentmind.rate-limit.mode", "disabled")
                .withProperty("spring.datasource.password", "agentmind_dev_password");

        assertThatThrownBy(() -> new ProductionConfigurationValidator(environment).afterSingletonsInstantiated())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("正式环境配置验收失败")
                .hasMessageContaining("agentmind.security.mode")
                .hasMessageContaining("agentmind.rate-limit.mode")
                .hasMessageNotContaining("agentmind_dev_password");
    }

    private MockEnvironment validEnvironment() {
        return new MockEnvironment()
                .withProperty("agentmind.security.mode", "oidc")
                .withProperty("agentmind.security.issuer-uri", "https://identity.example.com/realms/agentmind")
                .withProperty("agentmind.security.audience", "agentmind-api")
                .withProperty("agentmind.core.persistence.store", "jdbc")
                .withProperty("agentmind.rate-limit.mode", "redis")
                .withProperty("agentmind.rate-limit.fail-open", "false")
                .withProperty("agentmind.storage.type", "minio")
                .withProperty("agentmind.vector-store.type", "pgvector")
                .withProperty("agentmind.keyword-index.type", "opensearch")
                .withProperty("agentmind.knowledge-index.outbox.enabled", "true")
                .withProperty("agentmind.chat.memory.store", "redis")
                .withProperty("spring.datasource.url", "jdbc:postgresql://db:5432/agentmind")
                .withProperty("spring.datasource.username", "agentmind")
                .withProperty("spring.datasource.password", "database-secret-value")
                .withProperty("spring.data.redis.password", "redis-secret-value")
                .withProperty("agentmind.storage.minio.access-key", "minio-access-value")
                .withProperty("agentmind.storage.minio.secret-key", "minio-secret-value")
                .withProperty("agentmind.keyword-index.opensearch.base-url", "https://search.example.com")
                .withProperty("agentmind.keyword-index.opensearch.username", "agentmind")
                .withProperty("agentmind.keyword-index.opensearch.password", "search-secret-value")
                .withProperty("agentmind.web.allowed-origin", "https://knowledge.example.com");
    }
}
