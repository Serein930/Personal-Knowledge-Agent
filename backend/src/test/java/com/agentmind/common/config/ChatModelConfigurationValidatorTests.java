package com.agentmind.common.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** 验证真实聊天模型的协议地址和模型名称可以在启动阶段被准确检查。 */
class ChatModelConfigurationValidatorTests {

    @Test
    void validDeepSeekOpenAiCompatibleConfigurationShouldPass() {
        MockEnvironment environment = deepSeekEnvironment()
                .withProperty("spring.ai.openai.chat.options.model", "deepseek-v4-pro");

        assertThatCode(() -> validator(environment).afterSingletonsInstantiated())
                .doesNotThrowAnyException();
    }

    @Test
    void anthropicAddressShouldFailWithActionableMessage() {
        MockEnvironment environment = deepSeekEnvironment()
                .withProperty("spring.ai.openai.chat.base-url", "https://api.deepseek.com/anthropic")
                .withProperty("spring.ai.openai.chat.options.model", "deepseek-v4-pro");

        assertThatThrownBy(() -> validator(environment).afterSingletonsInstantiated())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不能包含 /anthropic")
                .hasMessageContaining("https://api.deepseek.com");
    }

    @Test
    void malformedDeepSeekModelNameShouldFailWithCorrectExample() {
        MockEnvironment environment = deepSeekEnvironment()
                .withProperty("spring.ai.openai.chat.options.model", "deepseekv4-pro");

        assertThatThrownBy(() -> validator(environment).afterSingletonsInstantiated())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("缺少连字符")
                .hasMessageContaining("deepseek-v4-pro");
    }

    @Test
    void duplicatedVersionPathShouldFail() {
        MockEnvironment environment = deepSeekEnvironment()
                .withProperty("spring.ai.openai.chat.base-url", "https://api.deepseek.com/v1")
                .withProperty("spring.ai.openai.chat.completions-path", "/v1/chat/completions")
                .withProperty("spring.ai.openai.chat.options.model", "deepseek-v4-pro");

        assertThatThrownBy(() -> validator(environment).afterSingletonsInstantiated())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("重复包含 /v1");
    }

    private ChatModelConfigurationValidator validator(MockEnvironment environment) {
        return new ChatModelConfigurationValidator(environment);
    }

    private MockEnvironment deepSeekEnvironment() {
        return new MockEnvironment()
                .withProperty("spring.ai.model.chat", "openai")
                .withProperty("spring.ai.openai.chat.base-url", "https://api.deepseek.com")
                .withProperty("spring.ai.openai.chat.completions-path", "/v1/chat/completions");
    }
}
