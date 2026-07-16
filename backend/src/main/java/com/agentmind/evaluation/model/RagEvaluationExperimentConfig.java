package com.agentmind.evaluation.model;

/**
 * 一次评估任务冻结的实验配置。
 *
 * <p>配置随任务持久化，保证即使系统默认参数后来变化，历史结果仍能解释当时使用的
 * chunk 策略、候选池、检索方式、重排方式、提示词和模型。</p>
 */
public record RagEvaluationExperimentConfig(
        String experimentName,
        String chunkStrategyVersion,
        RagEvaluationRetrievalStrategy retrievalStrategy,
        int candidatePoolSize,
        RagEvaluationRerankStrategy rerankStrategy,
        int topK,
        String promptVersion,
        String modelName
) {
}
