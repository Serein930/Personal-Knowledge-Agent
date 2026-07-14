package com.agentmind.agent.proposal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 写工具建议生成配置。
 *
 * <p>建议生成和写工具执行使用两个完全独立的开关。即使启用真实模型，模型也只能返回结构化建议，
 * 真正写入仍必须经过确认单令牌、权限复核和事务边界。</p>
 */
@Component
@ConfigurationProperties(prefix = "agentmind.agent.write-proposal")
public class WriteToolProposalProperties {

    private String generator = "rule";
    private String promptVersion = "write-tool-proposal-v1";
    private boolean fallbackToRuleEnabled = true;

    public String getGenerator() {
        return generator;
    }

    public void setGenerator(String generator) {
        this.generator = generator;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public void setPromptVersion(String promptVersion) {
        this.promptVersion = promptVersion;
    }

    public boolean isFallbackToRuleEnabled() {
        return fallbackToRuleEnabled;
    }

    public void setFallbackToRuleEnabled(boolean fallbackToRuleEnabled) {
        this.fallbackToRuleEnabled = fallbackToRuleEnabled;
    }
}
