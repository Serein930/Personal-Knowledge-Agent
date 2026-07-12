package com.agentmind.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 检索增强生成回答生成配置。
 *
 * <p>这些配置先服务于本地模拟生成链路，后续接入真实聊天模型时也沿用同一组开关，
 * 避免在控制层或业务服务中散落模型选择、置信度阈值和提示词版本等基础参数。</p>
 */
@Component
@ConfigurationProperties(prefix = "agentmind.rag")
public class RagAnswerGenerationProperties {

    private String answerGenerator = "mock";
    private String promptVersion = "rag-chat-v1";
    private String modelName = "mock-local";
    private double minimumCitationScore = 0.05;
    private int maxContextCitations = 5;
    private boolean springAiFailureFallbackEnabled = true;

    public String getAnswerGenerator() {
        return answerGenerator;
    }

    public void setAnswerGenerator(String answerGenerator) {
        this.answerGenerator = answerGenerator;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public void setPromptVersion(String promptVersion) {
        this.promptVersion = promptVersion;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public double getMinimumCitationScore() {
        return minimumCitationScore;
    }

    public void setMinimumCitationScore(double minimumCitationScore) {
        this.minimumCitationScore = minimumCitationScore;
    }

    public int getMaxContextCitations() {
        return maxContextCitations;
    }

    public void setMaxContextCitations(int maxContextCitations) {
        this.maxContextCitations = maxContextCitations;
    }

    public boolean isSpringAiFailureFallbackEnabled() {
        return springAiFailureFallbackEnabled;
    }

    public void setSpringAiFailureFallbackEnabled(boolean springAiFailureFallbackEnabled) {
        this.springAiFailureFallbackEnabled = springAiFailureFallbackEnabled;
    }
}
