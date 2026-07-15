package com.agentmind.study.flashcard.fsrs.optimization;

import com.agentmind.study.flashcard.model.StudyFlashcardReview;
import java.util.List;

/** FSRS 用户权重拟合端口，便于后续替换为离线 Python 训练服务或官方优化器。 */
public interface FsrsParameterOptimizer {

    FsrsOptimizationResult optimize(
            List<Double> initialParameters,
            double desiredRetention,
            List<StudyFlashcardReview> reviews
    );
}
