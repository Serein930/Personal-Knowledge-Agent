package com.agentmind.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 真实聊天模型的手动联调测试。
 *
 * <p>只有当前进程显式提供 {@code OPENAI_API_KEY} 时才运行。普通测试和持续集成会跳过本类，
 * 从而避免意外访问付费模型；运行结果用于确认自动配置、真实回答和 Token 元数据。</p>
 */
@SpringBootTest(properties = {
        "spring.ai.model.chat=openai",
        "spring.ai.model.embedding=none",
        "spring.ai.openai.chat.options.model=gpt-4o-mini",
        "agentmind.rag.answer-generator=spring-ai",
        "agentmind.rag.model-name=gpt-4o-mini",
        "agentmind.rag.tool-calling-enabled=false",
        "agentmind.rag.spring-ai-failure-fallback-enabled=false"
})
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class SpringAiRagManualIntegrationTests {

    @Autowired
    private AnswerGenerator answerGenerator;

    @Test
    void shouldGenerateGroundedAnswerWithRealProvider() {
        AnswerGenerationRequest request = new AnswerGenerationRequest(
                1L,
                "只根据上下文回答：线程池最主要的作用是什么？",
                "rag-chat-v1",
                "线程池通过复用工作线程处理多个任务，并通过队列和最大线程数限制并发资源。",
                "请只根据给定上下文，用一句中文回答线程池最主要的作用。",
                List.of(),
                new RagRefusalDecision(false, "")
        );

        GeneratedAnswer answer = answerGenerator.generate(request);

        assertThat(answer.content()).isNotBlank();
        assertThat(answer.metadata().answerGenerator()).isEqualTo("spring-ai");
        assertThat(answer.metadata().modelName()).isEqualTo("gpt-4o-mini");
        assertThat(answer.usage().totalTokens()).isPositive();
    }
}
