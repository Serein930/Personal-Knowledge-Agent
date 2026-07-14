package com.agentmind.study.flashcard.algorithm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentmind.study.flashcard.model.StudyFlashcardSchedule;
import com.agentmind.study.flashcard.model.StudyFlashcardStatus;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

/**
 * SM-2 固定输入输出测试，防止后续重构意外改变调度语义。
 */
class Sm2SpacedRepetitionAlgorithmTests {

    private final Sm2SpacedRepetitionAlgorithm algorithm = new Sm2SpacedRepetitionAlgorithm();
    private final OffsetDateTime baseTime = OffsetDateTime.parse("2026-07-15T08:00:00+08:00");

    @Test
    void successfulReviewsShouldProgressFromLearningToReview() {
        StudyFlashcardSchedule initial = new StudyFlashcardSchedule(
                StudyFlashcardStatus.NEW, 0, 0, 2.5, 0, baseTime, null
        );

        StudyFlashcardSchedule first = algorithm.calculate(initial, 5, baseTime);
        StudyFlashcardSchedule second = algorithm.calculate(first, 5, first.dueAt());
        StudyFlashcardSchedule third = algorithm.calculate(second, 4, second.dueAt());

        assertThat(first.status()).isEqualTo(StudyFlashcardStatus.LEARNING);
        assertThat(first.repetitionCount()).isEqualTo(1);
        assertThat(first.intervalDays()).isEqualTo(1);
        assertThat(first.easeFactor()).isEqualTo(2.6);
        assertThat(second.status()).isEqualTo(StudyFlashcardStatus.REVIEW);
        assertThat(second.intervalDays()).isEqualTo(6);
        assertThat(third.status()).isEqualTo(StudyFlashcardStatus.REVIEW);
        assertThat(third.repetitionCount()).isEqualTo(3);
        assertThat(third.intervalDays()).isEqualTo(16);
    }

    @Test
    void failedReviewShouldResetRepetitionAndIncreaseLapseCount() {
        StudyFlashcardSchedule reviewState = new StudyFlashcardSchedule(
                StudyFlashcardStatus.REVIEW, 4, 20, 2.5, 1, baseTime, baseTime.minusDays(20)
        );

        StudyFlashcardSchedule failed = algorithm.calculate(reviewState, 2, baseTime);

        assertThat(failed.status()).isEqualTo(StudyFlashcardStatus.LEARNING);
        assertThat(failed.repetitionCount()).isZero();
        assertThat(failed.intervalDays()).isEqualTo(1);
        assertThat(failed.lapseCount()).isEqualTo(2);
        assertThat(failed.easeFactor()).isEqualTo(2.18);
        assertThat(failed.dueAt()).isEqualTo(baseTime.plusDays(1));
    }

    @Test
    void scoreOutsideSm2RangeShouldBeRejected() {
        StudyFlashcardSchedule initial = new StudyFlashcardSchedule(
                StudyFlashcardStatus.NEW, 0, 0, 2.5, 0, baseTime, null
        );

        assertThatThrownBy(() -> algorithm.calculate(initial, 6, baseTime))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0 到 5");
    }
}
