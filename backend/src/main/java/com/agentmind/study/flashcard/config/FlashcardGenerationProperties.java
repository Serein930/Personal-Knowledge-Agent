package com.agentmind.study.flashcard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 从知识资产生成复习卡片的配置。
 *
 * <p>生成模式与 RAG 回答模式分开配置，避免因为关闭智能体写工具而影响用户主动制卡。
 * 本地模式完全可重复；真实模型模式负责生成更自然的原子问答，并允许在模型异常时安全降级。</p>
 */
@Component
@ConfigurationProperties(prefix = "agentmind.study.flashcard.generation")
public class FlashcardGenerationProperties {

    private String provider = "local";
    private String promptVersion = "flashcard-atomic-v1";
    private String modelName = "mock-local";
    private int maximumSourceCharacters = 16_000;
    private int maximumAnswerCharacters = 360;
    private boolean failureFallbackEnabled = true;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
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

    public int getMaximumSourceCharacters() {
        return maximumSourceCharacters;
    }

    public void setMaximumSourceCharacters(int maximumSourceCharacters) {
        this.maximumSourceCharacters = maximumSourceCharacters;
    }

    public int getMaximumAnswerCharacters() {
        return maximumAnswerCharacters;
    }

    public void setMaximumAnswerCharacters(int maximumAnswerCharacters) {
        this.maximumAnswerCharacters = maximumAnswerCharacters;
    }

    public boolean isFailureFallbackEnabled() {
        return failureFallbackEnabled;
    }

    public void setFailureFallbackEnabled(boolean failureFallbackEnabled) {
        this.failureFallbackEnabled = failureFallbackEnabled;
    }
}
