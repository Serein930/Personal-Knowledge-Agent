package com.agentmind.study.flashcard.algorithm;

import com.agentmind.study.flashcard.model.StudyFlashcardReview;
import com.agentmind.study.flashcard.model.StudyFlashcardSchedule;
import com.agentmind.study.flashcard.model.StudyFlashcardStatus;
import com.agentmind.study.flashcard.fsrs.model.FsrsCardSnapshot;
import com.agentmind.study.flashcard.fsrs.model.FsrsUserProfile;
import io.github.openspacedrepetition.Card;
import io.github.openspacedrepetition.CardAndReviewLog;
import io.github.openspacedrepetition.Rating;
import io.github.openspacedrepetition.Scheduler;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于官方 Java-FSRS 库的可选调度适配器。
 *
 * <p>当前领域模型尚未直接持久化 FSRS 的稳定度与难度，因此适配器会按照时间顺序重放卡片历史，
 * 重建算法状态后再计算本次评分。这种方式优先保证迁移正确性与可解释性；当单卡历史很长时，
 * 后续可把序列化后的 FSRS 卡片状态作为快照保存，避免每次完整重放。</p>
 */
@Component
@ConditionalOnProperty(prefix = "agentmind.study.flashcard", name = "algorithm", havingValue = "fsrs")
public class FsrsSpacedRepetitionAlgorithm implements StatefulSpacedRepetitionAlgorithm {

    private static final Logger LOGGER = LoggerFactory.getLogger(FsrsSpacedRepetitionAlgorithm.class);
    public static final String ALGORITHM_NAME = "fsrs-6-java-1.0.0";
    public static final int SNAPSHOT_SCHEMA_VERSION = 1;

    @Override
    public String name() {
        return ALGORITHM_NAME;
    }

    @Override
    public StudyFlashcardSchedule calculate(
            StudyFlashcardSchedule current,
            int score,
            OffsetDateTime reviewedAt,
            List<StudyFlashcardReview> history
    ) {
        FsrsUserProfile defaultProfile = new FsrsUserProfile(
                0L,
                java.util.Arrays.stream(Scheduler.builder().build().getParameters()).boxed().toList(),
                Scheduler.builder().build().getDesiredRetention(),
                0,
                com.agentmind.study.flashcard.fsrs.model.FsrsUserProfileSource.DEFAULT,
                reviewedAt,
                reviewedAt
        );
        return calculateWithSnapshot(
                stableFlashcardId(history), current, score, reviewedAt, history, Optional.empty(), defaultProfile
        ).schedule();
    }

    @Override
    public StatefulCalculation calculateWithSnapshot(
            Long flashcardId,
            StudyFlashcardSchedule current,
            int score,
            OffsetDateTime reviewedAt,
            List<StudyFlashcardReview> history,
            Optional<FsrsCardSnapshot> currentSnapshot,
            FsrsUserProfile userProfile
    ) {
        validateScore(score);
        Scheduler scheduler = createScheduler(userProfile);
        Card card = restoreOrReplay(scheduler, flashcardId, history, currentSnapshot, userProfile);

        CardAndReviewLog result = scheduler.reviewCard(card, mapRating(score), reviewedAt.toInstant());
        Card next = result.card();
        int intervalDays = Math.max(1, (int) ChronoUnit.DAYS.between(reviewedAt.toInstant(), next.getDue()));
        int repetitionCount = score < 3 ? 0 : current.repetitionCount() + 1;
        int lapseCount = score < 3 ? current.lapseCount() + 1 : current.lapseCount();
        StudyFlashcardStatus status = score < 3 ? StudyFlashcardStatus.LEARNING : StudyFlashcardStatus.REVIEW;

        return new StatefulCalculation(
                new StudyFlashcardSchedule(
                        status,
                        repetitionCount,
                        intervalDays,
                        current.easeFactor(),
                        lapseCount,
                        next.getDue().atOffset(ZoneOffset.UTC),
                        reviewedAt
                ),
                next.toJson(),
                SNAPSHOT_SCHEMA_VERSION
        );
    }

    private Scheduler createScheduler(FsrsUserProfile profile) {
        double[] parameters = profile.parameters().stream().mapToDouble(Double::doubleValue).toArray();
        return Scheduler.builder()
                .parameters(parameters)
                .desiredRetention(profile.desiredRetention())
                // 当前产品以“天”为最小复习单位，关闭分钟级学习步骤和随机扰动，保证自动测试可重复。
                .learningSteps(new Duration[]{})
                .relearningSteps(new Duration[]{})
                .enableFuzzing(false)
                .build();
    }

    private Card restoreOrReplay(
            Scheduler scheduler,
            Long flashcardId,
            List<StudyFlashcardReview> history,
            Optional<FsrsCardSnapshot> currentSnapshot,
            FsrsUserProfile profile
    ) {
        FsrsCardSnapshot snapshot = currentSnapshot
                .filter(value -> value.schemaVersion() == SNAPSHOT_SCHEMA_VERSION)
                .filter(value -> ALGORITHM_NAME.equals(value.algorithmVersion()))
                // 参数改变后重新计算一次历史，避免把旧参数产生的内部状态继续用于新模型。
                .filter(value -> value.profileVersion() == profile.version())
                .orElse(null);
        if (snapshot != null) {
            try {
                return Card.fromJson(snapshot.payload());
            } catch (RuntimeException exception) {
                LOGGER.warn("FSRS 卡片快照无法解析，改用历史记录重建：卡片编号={}", flashcardId);
            }
        }
        return replayHistory(scheduler, flashcardId, history);
    }

    private Card replayHistory(Scheduler scheduler, Long flashcardId, List<StudyFlashcardReview> history) {
        Card card = Card.builder().cardId(Long.hashCode(flashcardId == null ? 1L : flashcardId)).build();
        List<StudyFlashcardReview> chronological = history.stream()
                .sorted(Comparator.comparing(StudyFlashcardReview::reviewedAt)
                        .thenComparing(StudyFlashcardReview::id, Comparator.nullsLast(Long::compareTo)))
                .toList();
        for (StudyFlashcardReview review : chronological) {
            card = scheduler.reviewCard(card, mapRating(review.score()), review.reviewedAt().toInstant()).card();
        }
        return card;
    }

    private Long stableFlashcardId(List<StudyFlashcardReview> history) {
        return history.isEmpty() || history.getFirst().flashcardId() == null
                ? 1L
                : history.getFirst().flashcardId();
    }

    private Rating mapRating(int score) {
        return switch (score) {
            case 0, 1, 2 -> Rating.AGAIN;
            case 3 -> Rating.HARD;
            case 4 -> Rating.GOOD;
            case 5 -> Rating.EASY;
            default -> throw new IllegalArgumentException("FSRS 评分必须在 0 到 5 之间");
        };
    }

    private void validateScore(int score) {
        if (score < 0 || score > 5) {
            throw new IllegalArgumentException("FSRS 评分必须在 0 到 5 之间");
        }
    }
}
