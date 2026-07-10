package com.agentmind.chat.model.dto;

/**
 * 检索增强生成回答生成元数据。
 *
 * <p>该结构记录本次回答使用的提示词版本、回答生成器、模型名称、拒答状态和耗时。
 * 这些字段可以帮助前端展示调试信息，也为后续检索增强生成评估与问题排查保留依据。</p>
 */
public record RagAnswerGenerationMetadataResponse(
        String promptVersion,
        String answerGenerator,
        String modelName,
        boolean refused,
        String refusalReason,
        long elapsedMillis
) {
}
