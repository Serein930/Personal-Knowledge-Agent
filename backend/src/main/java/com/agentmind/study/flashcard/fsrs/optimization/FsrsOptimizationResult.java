package com.agentmind.study.flashcard.fsrs.optimization;

import java.util.List;

/**
 * FSRS 权重拟合结果。
 *
 * <p>训练损失用于判断优化器是否真正学习到历史规律，验证损失用于阻止只记住训练样本的参数
 * 被直接应用。调用方只有在 {@link #accepted()} 为真时才允许写入用户参数版本。</p>
 */
public record FsrsOptimizationResult(
        List<Double> parameters,
        double trainingLossBefore,
        double trainingLossAfter,
        double validationLossBefore,
        double validationLossAfter,
        int effectiveObservationCount,
        boolean accepted
) {

    public FsrsOptimizationResult {
        parameters = List.copyOf(parameters);
    }
}
