package com.agentmind.study.flashcard.algorithm;

import com.agentmind.study.flashcard.model.StudyFlashcardSchedule;
import com.agentmind.study.flashcard.model.StudyFlashcardStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * SM-2 间隔重复算法实现。
 *
 * <p>评分低于 3 表示回忆失败，重复次数归零并在一天后重新学习；评分达到 3 后，前两次成功间隔固定为
 * 1 天和 6 天，之后按当前间隔乘以更新后的难度因子增长。难度因子最低为 1.3，避免卡片永久停滞。</p>
 */
@Component
@ConditionalOnProperty(
        prefix = "agentmind.study.flashcard",
        name = "algorithm",
        havingValue = "sm2",
        matchIfMissing = true
)
public class Sm2SpacedRepetitionAlgorithm implements SpacedRepetitionAlgorithm {

    public static final String ALGORITHM_NAME = "sm2-v1";
    private static final double MINIMUM_EASE_FACTOR = 1.3;

    @Override
    public String name() {
        return ALGORITHM_NAME;
    }

    @Override
    public StudyFlashcardSchedule calculate(
            StudyFlashcardSchedule current,
            int score,
            java.time.OffsetDateTime reviewedAt
    ) {
        if (score < 0 || score > 5) {
            throw new IllegalArgumentException("SM-2 评分必须在 0 到 5 之间");
        }
        double nextEaseFactor = calculateEaseFactor(current.easeFactor(), score);
        if (score < 3) {
            int intervalDays = 1;
            return new StudyFlashcardSchedule(
                    StudyFlashcardStatus.LEARNING,
                    0,
                    intervalDays,
                    nextEaseFactor,
                    current.lapseCount() + 1,
                    reviewedAt.plusDays(intervalDays),
                    reviewedAt
            );
        }

        int nextRepetitionCount = current.repetitionCount() + 1;
        int nextIntervalDays = switch (nextRepetitionCount) {
            case 1 -> 1;
            case 2 -> 6;
            default -> Math.max(1, (int) Math.round(current.intervalDays() * nextEaseFactor));
        };
        StudyFlashcardStatus nextStatus = nextRepetitionCount >= 2
                ? StudyFlashcardStatus.REVIEW
                : StudyFlashcardStatus.LEARNING;
        return new StudyFlashcardSchedule(
                nextStatus,
                nextRepetitionCount,
                nextIntervalDays,
                nextEaseFactor,
                current.lapseCount(),
                reviewedAt.plusDays(nextIntervalDays),
                reviewedAt
        );
    }

    private double calculateEaseFactor(double currentEaseFactor, int score) {
        int distanceFromPerfect = 5 - score;
        double delta = 0.1 - distanceFromPerfect * (0.08 + distanceFromPerfect * 0.02);
        double calculated = Math.max(MINIMUM_EASE_FACTOR, currentEaseFactor + delta);
        return Math.round(calculated * 100.0) / 100.0;
    }
}
