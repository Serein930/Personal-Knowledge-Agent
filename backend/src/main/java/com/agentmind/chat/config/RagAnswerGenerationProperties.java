package com.agentmind.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 检索增强生成回答生成配置。
 *
 * <p>Mock 与 Spring AI 真实模型共用本配置，统一管理模型选择、置信度阈值、提示词版本、
 * 流式超时和失败降级策略，避免这些运行参数散落在控制层或业务编排代码中。</p>
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
    private long streamTimeoutMillis = 60_000;
    private int streamChunkSize = 24;
    private boolean toolCallingEnabled = true;
    private int maxToolRoundTrips = 4;
    private boolean writeToolProposalsEnabled = true;

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

    public long getStreamTimeoutMillis() {
        return streamTimeoutMillis;
    }

    public void setStreamTimeoutMillis(long streamTimeoutMillis) {
        this.streamTimeoutMillis = streamTimeoutMillis;
    }

    public int getStreamChunkSize() {
        return streamChunkSize;
    }

    public void setStreamChunkSize(int streamChunkSize) {
        this.streamChunkSize = streamChunkSize;
    }

    public boolean isToolCallingEnabled() {
        return toolCallingEnabled;
    }

    public void setToolCallingEnabled(boolean toolCallingEnabled) {
        this.toolCallingEnabled = toolCallingEnabled;
    }

    public int getMaxToolRoundTrips() {
        return maxToolRoundTrips;
    }

    public void setMaxToolRoundTrips(int maxToolRoundTrips) {
        this.maxToolRoundTrips = maxToolRoundTrips;
    }

    public boolean isWriteToolProposalsEnabled() {
        return writeToolProposalsEnabled;
    }

    public void setWriteToolProposalsEnabled(boolean writeToolProposalsEnabled) {
        this.writeToolProposalsEnabled = writeToolProposalsEnabled;
    }
}
