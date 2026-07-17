package com.agentmind.knowledge.vector;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 真实向量模型的手动联调测试。
 *
 * <p>只有显式提供 {@code OPENAI_API_KEY} 时才会运行，因此常规测试和持续集成不会产生外部调用与费用。
 * 该测试用于验证 Spring AI 自动配置、模型权限和实际返回维度，不能替代默认的可重复单元测试。</p>
 */
@SpringBootTest(properties = {
        "agentmind.embedding.provider=spring-ai",
        "agentmind.embedding.model-name=text-embedding-3-small",
        "agentmind.embedding.dimensions=128",
        "spring.ai.model.embedding=openai",
        "spring.ai.openai.embedding.options.model=text-embedding-3-small",
        "spring.ai.openai.embedding.options.dimensions=128"
})
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class SpringAiEmbeddingManualIntegrationTests {

    @Autowired
    private EmbeddingClient embeddingClient;

    @Test
    void shouldGenerateVectorWithRealProvider() {
        float[] vector = embeddingClient.embed("AgentMind 真实向量模型联调");

        assertThat(vector).hasSize(128);
        boolean containsNonZeroValue = false;
        for (float value : vector) {
            if (value != 0F) {
                containsNonZeroValue = true;
                break;
            }
        }
        assertThat(containsNonZeroValue).isTrue();
    }
}
