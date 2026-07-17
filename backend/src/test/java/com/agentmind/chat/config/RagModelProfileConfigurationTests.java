package com.agentmind.chat.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/** 验证真实模型 profile 不会因为公共默认配置而静默退回 Mock。 */
class RagModelProfileConfigurationTests {

    private final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

    @Test
    void productionProfileShouldForceSpringAiAnswerGenerator() throws IOException {
        List<PropertySource<?>> sources = loader.load(
                "production",
                new ClassPathResource("application-production.yml")
        );

        assertThat(property(sources, "agentmind.rag.answer-generator")).isEqualTo("spring-ai");
        assertThat(property(sources, "agentmind.rag.observation-store")).isEqualTo("jdbc");
        assertThat(property(sources, "spring.ai.model.chat")).isEqualTo(
                "${AGENTMIND_SPRING_AI_CHAT_MODEL:openai}");
    }

    @Test
    void localAiProfileShouldEnableRealChatWithoutForcingRealEmbedding() throws IOException {
        List<PropertySource<?>> sources = loader.load(
                "local-ai",
                new ClassPathResource("application-local-ai.yml")
        );

        assertThat(property(sources, "agentmind.rag.answer-generator")).isEqualTo("spring-ai");
        assertThat(property(sources, "spring.ai.model.chat")).isEqualTo(
                "${AGENTMIND_SPRING_AI_CHAT_MODEL:openai}");
        assertThat(property(sources, "spring.ai.model.embedding")).isNull();
    }

    private Object property(List<PropertySource<?>> sources, String name) {
        return sources.stream()
                .map(source -> source.getProperty(name))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }
}
