package com.agentmind.study.flashcard.fsrs.optimization;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentmind.study.flashcard.model.StudyFlashcardReview;
import com.agentmind.study.flashcard.model.StudyFlashcardStatus;
import io.github.openspacedrepetition.Scheduler;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** FSRS 权重拟合器的固定数据集测试。 */
class CoordinateDescentFsrsParameterOptimizerTests {

    private final CoordinateDescentFsrsParameterOptimizer optimizer =
            new CoordinateDescentFsrsParameterOptimizer();

    @Test
    void optimizerShouldReplayHistoryAndReturnDeterministicLossMetrics() {
        List<Double> parameters = Arrays.stream(Scheduler.builder().build().getParameters()).boxed().toList();
        List<StudyFlashcardReview> reviews = syntheticReviews();

        FsrsOptimizationResult first = optimizer.optimize(parameters, 0.9, reviews);
        FsrsOptimizationResult second = optimizer.optimize(parameters, 0.9, reviews);

        assertThat(first).isEqualTo(second);
        assertThat(first.parameters()).hasSameSizeAs(parameters);
        assertThat(first.effectiveObservationCount()).isEqualTo(90);
        assertThat(first.trainingLossBefore()).isPositive().isLessThan(1_000_000);
        assertThat(first.validationLossBefore()).isPositive().isLessThan(1_000_000);
        assertThat(first.trainingLossAfter()).isLessThanOrEqualTo(first.trainingLossBefore());
        assertThat(first.parameters()).allMatch(Double::isFinite);
    }

    private List<StudyFlashcardReview> syntheticReviews() {
        List<StudyFlashcardReview> reviews = new ArrayList<>();
        OffsetDateTime start = OffsetDateTime.parse("2026-01-01T08:00:00+08:00");
        long reviewId = 1;
        for (long cardId = 1; cardId <= 10; cardId++) {
            for (int attempt = 0; attempt < 10; attempt++) {
                int score = attempt > 0 && (attempt + cardId) % 5 == 0 ? 2 : 4;
                OffsetDateTime reviewedAt = start.plusDays(attempt * 3L + cardId);
                reviews.add(new StudyFlashcardReview(
                        reviewId++, 1L, 1L, cardId, "fit-" + cardId + "-" + attempt,
                        score, StudyFlashcardStatus.REVIEW, StudyFlashcardStatus.REVIEW,
                        3, 3, 2.5, 2.5, reviewedAt.minusDays(1), reviewedAt.plusDays(3),
                        "fsrs-test", reviewedAt, reviewedAt
                ));
            }
        }
        return reviews;
    }
}
