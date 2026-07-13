package com.agentmind.chat.memory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 短期会话记忆配置。
 *
 * <p>消息数量限制用于控制窗口轮次，字符数限制用于约束近似上下文体积。后续接入真实模型时，
 * 可以在服务层继续扩展为基于令牌数的精确预算。</p>
 */
@Component
@ConfigurationProperties(prefix = "agentmind.chat.memory")
public class ChatMemoryProperties {

    private String store = "memory";
    private int maxMessages = 12;
    private int maxContextChars = 6_000;

    public String getStore() {
        return store;
    }

    public void setStore(String store) {
        this.store = store;
    }

    public int getMaxMessages() {
        return maxMessages;
    }

    public void setMaxMessages(int maxMessages) {
        this.maxMessages = maxMessages;
    }

    public int getMaxContextChars() {
        return maxContextChars;
    }

    public void setMaxContextChars(int maxContextChars) {
        this.maxContextChars = maxContextChars;
    }
}
