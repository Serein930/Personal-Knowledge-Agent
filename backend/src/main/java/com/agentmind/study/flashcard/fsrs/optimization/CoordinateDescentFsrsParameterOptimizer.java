package com.agentmind.study.flashcard.fsrs.optimization;

import com.agentmind.study.flashcard.model.StudyFlashcardReview;
import io.github.openspacedrepetition.Card;
import io.github.openspacedrepetition.Rating;
import io.github.openspacedrepetition.Scheduler;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 使用历史回放和坐标下降拟合 FSRS 权重。
 *
 * <p>每次复习发生前，优化器先用候选参数计算卡片当时的可回忆概率，再把用户评分转换成
 * “记住/遗忘”标签并计算二元对数损失。全部事件随后继续送入 FSRS 状态机，因而损失会真实
 * 反映稳定性、难度及其演化，而不是简单统计平均分。</p>
 *
 * <p>最后 20% 时间序列事件只参与验证，不参与坐标选择。候选参数还带有相对初始权重的轻量
 * 正则项，避免个人样本较少时产生极端权重。实现刻意保持确定性，常规测试无需模型或随机种子。</p>
 */
@Component
public class CoordinateDescentFsrsParameterOptimizer implements FsrsParameterOptimizer {

    private static final int OPTIMIZATION_PASSES = 3;
    private static final double VALIDATION_RATIO = 0.20;
    private static final double MINIMUM_ACCEPTED_IMPROVEMENT = 0.001;
    private static final double REGULARIZATION = 0.0005;
    private static final double MINIMUM_PROBABILITY = 0.000001;
    private static final double INVALID_LOSS = 1_000_000;

    @Override
    public FsrsOptimizationResult optimize(
            List<Double> initialParameters,
            double desiredRetention,
            List<StudyFlashcardReview> reviews
    ) {
        List<StudyFlashcardReview> chronological = reviews.stream()
                .sorted(Comparator.comparing(StudyFlashcardReview::reviewedAt)
                        .thenComparing(StudyFlashcardReview::id, Comparator.nullsLast(Long::compareTo)))
                .toList();
        OffsetDateTime validationStart = validationStart(chronological);
        double[] initial = initialParameters.stream().mapToDouble(Double::doubleValue).toArray();
        double[] candidate = initial.clone();
        double trainingBefore = loss(initial, initial, desiredRetention, chronological, validationStart, false);
        double validationBefore = loss(initial, initial, desiredRetention, chronological, validationStart, true);
        double bestTraining = trainingBefore;

        for (int pass = 0; pass < OPTIMIZATION_PASSES; pass++) {
            for (int index = 0; index < candidate.length; index++) {
                double step = Math.max(0.01, Math.abs(candidate[index]) * 0.08) / (pass + 1);
                double original = candidate[index];
                double bestValue = original;
                for (double direction : new double[]{-1.0, 1.0}) {
                    candidate[index] = original + direction * step;
                    double currentLoss = loss(
                            candidate, initial, desiredRetention, chronological, validationStart, false
                    );
                    if (currentLoss + 0.0000001 < bestTraining) {
                        bestTraining = currentLoss;
                        bestValue = candidate[index];
                    }
                }
                candidate[index] = bestValue;
            }
        }

        double validationAfter = loss(
                candidate, initial, desiredRetention, chronological, validationStart, true
        );
        int effectiveObservations = effectiveObservationCount(chronological);
        boolean accepted = Double.isFinite(bestTraining)
                && Double.isFinite(validationAfter)
                && bestTraining < trainingBefore
                && validationAfter <= validationBefore - MINIMUM_ACCEPTED_IMPROVEMENT;
        double[] result = accepted ? candidate : initial;
        return new FsrsOptimizationResult(
                toList(result), round(trainingBefore), round(accepted ? bestTraining : trainingBefore),
                round(validationBefore), round(accepted ? validationAfter : validationBefore),
                effectiveObservations, accepted
        );
    }

    private double loss(
            double[] parameters,
            double[] initial,
            double desiredRetention,
            List<StudyFlashcardReview> reviews,
            OffsetDateTime validationStart,
            boolean validation
    ) {
        Scheduler scheduler;
        try {
            scheduler = Scheduler.builder()
                    .parameters(parameters)
                    .desiredRetention(desiredRetention)
                    .learningSteps(new Duration[]{})
                    .relearningSteps(new Duration[]{})
                    .enableFuzzing(false)
                    .build();
        } catch (RuntimeException exception) {
            return INVALID_LOSS;
        }
        Map<Long, Card> cards = new HashMap<>();
        double sum = 0;
        int observations = 0;
        try {
            for (StudyFlashcardReview review : reviews) {
                Card card = cards.computeIfAbsent(
                        review.flashcardId(),
                        id -> Card.builder().cardId(Long.hashCode(id)).build()
                );
                boolean belongsToValidation = !review.reviewedAt().isBefore(validationStart);
                if (card.getLastReview() != null && belongsToValidation == validation) {
                    double probability = clamp(scheduler.getCardRetrievability(card, review.reviewedAt().toInstant()));
                    double label = review.score() >= 3 ? 1.0 : 0.0;
                    sum += -(label * Math.log(probability) + (1.0 - label) * Math.log(1.0 - probability));
                    observations++;
                }
                cards.put(
                        review.flashcardId(),
                        scheduler.reviewCard(card, mapRating(review.score()), review.reviewedAt().toInstant()).card()
                );
            }
        } catch (RuntimeException exception) {
            return INVALID_LOSS;
        }
        if (observations == 0) {
            return INVALID_LOSS;
        }
        return sum / observations + regularization(parameters, initial);
    }

    private double regularization(double[] parameters, double[] initial) {
        double sum = 0;
        for (int index = 0; index < parameters.length; index++) {
            double scale = Math.max(1.0, Math.abs(initial[index]));
            double normalizedDifference = (parameters[index] - initial[index]) / scale;
            sum += normalizedDifference * normalizedDifference;
        }
        return REGULARIZATION * sum;
    }

    private OffsetDateTime validationStart(List<StudyFlashcardReview> reviews) {
        int index = Math.max(1, (int) Math.floor(reviews.size() * (1.0 - VALIDATION_RATIO)));
        return reviews.get(Math.min(index, reviews.size() - 1)).reviewedAt();
    }

    private int effectiveObservationCount(List<StudyFlashcardReview> reviews) {
        Map<Long, Integer> counts = new HashMap<>();
        int observations = 0;
        for (StudyFlashcardReview review : reviews) {
            int count = counts.merge(review.flashcardId(), 1, Integer::sum);
            if (count > 1) {
                observations++;
            }
        }
        return observations;
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

    private double clamp(double value) {
        return Math.max(MINIMUM_PROBABILITY, Math.min(1.0 - MINIMUM_PROBABILITY, value));
    }

    private List<Double> toList(double[] values) {
        List<Double> result = new ArrayList<>(values.length);
        for (double value : values) {
            result.add(value);
        }
        return List.copyOf(result);
    }

    private double round(double value) {
        return Math.round(value * 1_000_000.0) / 1_000_000.0;
    }
}
