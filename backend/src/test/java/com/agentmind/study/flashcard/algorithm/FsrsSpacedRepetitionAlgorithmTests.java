package com.agentmind.study.flashcard.algorithm;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentmind.study.flashcard.model.StudyFlashcardReview;
import com.agentmind.study.flashcard.model.StudyFlashcardSchedule;
import com.agentmind.study.flashcard.model.StudyFlashcardStatus;
import com.agentmind.study.flashcard.fsrs.model.FsrsCardSnapshot;
import com.agentmind.study.flashcard.fsrs.model.FsrsUserProfile;
import com.agentmind.study.flashcard.fsrs.model.FsrsUserProfileSource;
import io.github.openspacedrepetition.Scheduler;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 官方 Java-FSRS 适配器的固定输入输出测试。
 */
class FsrsSpacedRepetitionAlgorithmTests {

    private final FsrsSpacedRepetitionAlgorithm algorithm = new FsrsSpacedRepetitionAlgorithm();

    @Test
    void historyShouldBeReplayedBeforeCalculatingNextSchedule() {
        OffsetDateTime firstReviewAt = OffsetDateTime.of(2026, 7, 1, 8, 0, 0, 0, ZoneOffset.UTC);
        StudyFlashcardReview history = new StudyFlashcardReview(
                1L, 1L, 1L, 9L, "fsrs-history-1", 4,
                StudyFlashcardStatus.NEW, StudyFlashcardStatus.REVIEW,
                0, 1, 2.5, 2.5, firstReviewAt, firstReviewAt.plusDays(1),
                "sm2-v1", firstReviewAt, firstReviewAt
        );
        OffsetDateTime reviewedAt = firstReviewAt.plusDays(3);
        StudyFlashcardSchedule current = new StudyFlashcardSchedule(
                StudyFlashcardStatus.REVIEW, 1, 1, 2.5, 0,
                firstReviewAt.plusDays(1), firstReviewAt
        );

        StudyFlashcardSchedule next = algorithm.calculate(current, 5, reviewedAt, List.of(history));

        assertThat(algorithm.name()).isEqualTo("fsrs-6-java-1.0.0");
        assertThat(next.status()).isEqualTo(StudyFlashcardStatus.REVIEW);
        assertThat(next.repetitionCount()).isEqualTo(2);
        assertThat(next.intervalDays()).isPositive();
        assertThat(next.dueAt()).isAfter(reviewedAt);
    }

    @Test
    void failedReviewShouldIncreaseLapseAndResetRepetition() {
        OffsetDateTime reviewedAt = OffsetDateTime.of(2026, 7, 10, 8, 0, 0, 0, ZoneOffset.UTC);
        StudyFlashcardSchedule current = new StudyFlashcardSchedule(
                StudyFlashcardStatus.REVIEW, 4, 20, 2.5, 1,
                reviewedAt.minusDays(1), reviewedAt.minusDays(20)
        );

        StudyFlashcardSchedule next = algorithm.calculate(current, 1, reviewedAt, List.of());

        assertThat(next.status()).isEqualTo(StudyFlashcardStatus.LEARNING);
        assertThat(next.repetitionCount()).isZero();
        assertThat(next.lapseCount()).isEqualTo(2);
    }

    @Test
    void persistedSnapshotShouldReplaceFullHistoryReplay() {
        OffsetDateTime firstReviewAt = OffsetDateTime.of(2026, 7, 12, 8, 0, 0, 0, ZoneOffset.UTC);
        FsrsUserProfile profile = defaultProfile(firstReviewAt);
        StudyFlashcardSchedule initial = new StudyFlashcardSchedule(
                StudyFlashcardStatus.NEW, 0, 0, 2.5, 0, firstReviewAt, null
        );
        StatefulSpacedRepetitionAlgorithm.StatefulCalculation first = algorithm.calculateWithSnapshot(
                88L, initial, 4, firstReviewAt, List.of(), Optional.empty(), profile
        );
        FsrsCardSnapshot snapshot = new FsrsCardSnapshot(
                1L, 1L, 88L, algorithm.name(), first.snapshotSchemaVersion(),
                profile.version(), first.snapshotPayload(), firstReviewAt, firstReviewAt
        );

        StatefulSpacedRepetitionAlgorithm.StatefulCalculation second = algorithm.calculateWithSnapshot(
                88L, first.schedule(), 5, firstReviewAt.plusDays(3),
                // 传入空历史可以证明第二次计算完全由已保存快照恢复。
                List.of(), Optional.of(snapshot), profile
        );

        assertThat(second.schedule().repetitionCount()).isEqualTo(2);
        assertThat(second.schedule().dueAt()).isAfter(firstReviewAt.plusDays(3));
        assertThat(second.snapshotPayload()).contains("stability").contains("difficulty");
    }

    private FsrsUserProfile defaultProfile(OffsetDateTime now) {
        Scheduler scheduler = Scheduler.builder().build();
        return new FsrsUserProfile(
                1L, Arrays.stream(scheduler.getParameters()).boxed().toList(),
                scheduler.getDesiredRetention(), 0, FsrsUserProfileSource.DEFAULT, now, now
        );
    }
}
