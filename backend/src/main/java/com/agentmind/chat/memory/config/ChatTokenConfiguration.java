package com.agentmind.chat.memory.config;

import com.agentmind.chat.memory.token.ChatTokenCounter;
import com.agentmind.chat.memory.token.SpringAiChatTokenCounter;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 会话令牌计算组件配置。
 *
 * <p>默认使用 Spring AI 提供的 JTokkit 估算器。条件化 Bean 允许测试或未来的模型适配模块
 * 注入其他实现，而不会与默认实现发生冲突。</p>
 */
@Configuration
public class ChatTokenConfiguration {

    @Bean
    @ConditionalOnMissingBean(ChatTokenCounter.class)
    public ChatTokenCounter chatTokenCounter() {
        return new SpringAiChatTokenCounter(new JTokkitTokenCountEstimator());
    }
}
