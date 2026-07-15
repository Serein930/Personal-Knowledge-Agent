package com.agentmind.study.flashcard.algorithm;

import com.agentmind.study.flashcard.model.StudyFlashcardReview;
import com.agentmind.study.flashcard.model.StudyFlashcardSchedule;
import com.agentmind.study.flashcard.model.StudyFlashcardStatus;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 基于官方 Java-FSRS 库的可选调度适配器。
 *
 * <p>当前领域模型尚未直接持久化 FSRS 的稳定度与难度，因此适配器会按照时间顺序重放卡片历史，
 * 重建算法状态后再计算本次评分。这种方式优先保证迁移正确性与可解释性；当单卡历史很长时，
 * 后续可把序列化后的 FSRS 卡片状态作为快照保存，避免每次完整重放。</p>
 */
@Component
@ConditionalOnProperty(prefix = "agentmind.study.flashcard", name = "algorithm", havingValue = "fsrs")
public class FsrsSpacedRepetitionAlgorithm implements SpacedRepetitionAlgorithm {

    public static final String ALGORITHM_NAME = "fsrs-6-java-1.0.0";

    private final Scheduler scheduler = Scheduler.builder()
            // 当前产品以“天”为最小复习单位，关闭分钟级学习步骤和随机扰动，保证自动测试可重复。
            .learningSteps(new Duration[]{})
            .relearningSteps(new Duration[]{})
            .enableFuzzing(false)
            .build();

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
        validateScore(score);
        Card card = Card.builder().cardId(stableCardId(history)).build();
        List<StudyFlashcardReview> chronological = history.stream()
                .sorted(Comparator.comparing(StudyFlashcardReview::reviewedAt)
                        .thenComparing(StudyFlashcardReview::id, Comparator.nullsLast(Long::compareTo)))
                .toList();
        for (StudyFlashcardReview review : chronological) {
            card = scheduler.reviewCard(
                    card,
                    mapRating(review.score()),
                    review.reviewedAt().toInstant()
            ).card();
        }

        CardAndReviewLog result = scheduler.reviewCard(card, mapRating(score), reviewedAt.toInstant());
        Card next = result.card();
        int intervalDays = Math.max(1, (int) ChronoUnit.DAYS.between(reviewedAt.toInstant(), next.getDue()));
        int repetitionCount = score < 3 ? 0 : current.repetitionCount() + 1;
        int lapseCount = score < 3 ? current.lapseCount() + 1 : current.lapseCount();
        StudyFlashcardStatus status = score < 3 ? StudyFlashcardStatus.LEARNING : StudyFlashcardStatus.REVIEW;

        return new StudyFlashcardSchedule(
                status,
                repetitionCount,
                intervalDays,
                current.easeFactor(),
                lapseCount,
                next.getDue().atOffset(ZoneOffset.UTC),
                reviewedAt
        );
    }

    private int stableCardId(List<StudyFlashcardReview> history) {
        if (history.isEmpty() || history.getFirst().flashcardId() == null) {
            return 1;
        }
        return Long.hashCode(history.getFirst().flashcardId());
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
