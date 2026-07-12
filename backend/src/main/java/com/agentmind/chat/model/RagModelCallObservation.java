package com.agentmind.chat.model;

/**
 * 检索增强生成模型调用观测记录。
 *
 * <p>当前阶段该结构只作为日志入参使用，不直接落库。后续接入数据库审计表或链路追踪系统时，
 * 可以把这里的字段平滑映射到持久化模型，避免日志字段和审计字段各自发散。</p>
 */
public record RagModelCallObservation(
        String promptVersion,
        String answerGenerator,
        String modelName,
        int citationCount,
        boolean refused,
        RagModelCallStatus status,
        long elapsedMillis,
        int answerLength,
        String failureReason
) {
}
