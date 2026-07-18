package com.agentmind.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/** 验证真实依赖 profile 明确选择持久化适配器，不继承公共配置中的内存默认值。 */
class PersistenceProfileConfigurationTests {

    private final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

    @Test
    void localProfileShouldUsePostgreSqlAndRedisRepositories() throws IOException {
        List<PropertySource<?>> sources = load("application-local.yml");

        assertPersistentRepositorySelection(sources);
        assertThat(property(sources, "agentmind.security.mode"))
                .isEqualTo("${AGENTMIND_SECURITY_MODE:local-jwt}");
        assertThat(property(sources, "agentmind.chat.memory.store")).isEqualTo("redis");
        assertThat(property(sources, "agentmind.vector-store.type")).isEqualTo("pgvector");
    }

    @Test
    void productionProfileShouldUsePostgreSqlAndRedisRepositories() throws IOException {
        List<PropertySource<?>> sources = load("application-production.yml");

        assertPersistentRepositorySelection(sources);
        assertThat(property(sources, "agentmind.chat.memory.store")).isEqualTo("redis");
        assertThat(property(sources, "agentmind.vector-store.type")).isEqualTo("pgvector");
    }

    private void assertPersistentRepositorySelection(List<PropertySource<?>> sources) {
        assertThat(property(sources, "agentmind.core.persistence.store")).isEqualTo("jdbc");
        assertThat(property(sources, "agentmind.agent.persistence.store")).isEqualTo("jdbc");
        assertThat(property(sources, "agentmind.rag.observation-store")).isEqualTo("jdbc");
        assertThat(property(sources, "agentmind.evaluation.store")).isEqualTo("jdbc");
    }

    private List<PropertySource<?>> load(String resourceName) throws IOException {
        return loader.load(resourceName, new ClassPathResource(resourceName));
    }

    private Object property(List<PropertySource<?>> sources, String name) {
        return sources.stream()
                .map(source -> source.getProperty(name))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }
}
