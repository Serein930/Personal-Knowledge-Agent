package com.agentmind.study.flashcard.service;

import com.agentmind.agent.service.AgentToolExecutionAuthorizer;
import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.study.flashcard.model.StudyFlashcard;
import com.agentmind.study.flashcard.model.StudyFlashcardReview;
import com.agentmind.study.flashcard.model.StudyFlashcardStatus;
import com.agentmind.study.flashcard.model.dto.StudyReviewStatisticsResponse;
import com.agentmind.study.flashcard.repository.StudyFlashcardRepository;
import com.agentmind.study.flashcard.repository.StudyFlashcardReviewRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 学习统计应用服务。
 *
 * <p>所有聚合均先经过用户与知识空间授权。当前阶段使用仓储返回的领域记录完成计算，便于内存与
 * PostgreSQL 实现共享同一套口径；数据量增长后可把聚合下推为数据库投影而不改变控制层契约。</p>
 */
@Service
public class StudyReviewStatisticsService {

    private static final int MATURE_INTERVAL_DAYS = 21;

    private final StudyFlashcardRepository flashcardRepository;
    private final StudyFlashcardReviewRepository reviewRepository;
    private final AgentToolExecutionAuthorizer authorizer;
    private final ZoneId studyZone;

    public StudyReviewStatisticsService(
            StudyFlashcardRepository flashcardRepository,
            StudyFlashcardReviewRepository reviewRepository,
            AgentToolExecutionAuthorizer authorizer,
            @Value("${agentmind.study.time-zone:Asia/Shanghai}") String studyTimeZone
    ) {
        this.flashcardRepository = flashcardRepository;
        this.reviewRepository = reviewRepository;
        this.authorizer = authorizer;
        this.studyZone = ZoneId.of(studyTimeZone);
    }

    public StudyReviewStatisticsResponse summarize(AgentToolExecutionContext context) {
        authorizer.authorize(context);
        OffsetDateTime now = OffsetDateTime.now();
        LocalDate today = now.atZoneSameInstant(studyZone).toLocalDate();
        List<StudyFlashcardReview> reviews = reviewRepository.findAllByOwnerUserIdAndWorkspaceId(
                context.ownerUserId(), context.workspaceId()
        );
        List<StudyFlashcard> cards = flashcardRepository.findAllByOwnerUserIdAndWorkspaceId(
                context.ownerUserId(), context.workspaceId()
        );

        List<StudyFlashcardReview> todayReviews = reviews.stream()
                .filter(review -> toStudyDate(review.reviewedAt()).equals(today))
                .toList();
        long successfulToday = todayReviews.stream().filter(review -> review.score() >= 3).count();
        long failures = reviews.stream().filter(review -> review.score() < 3).count();
        long dueCount = flashcardRepository.countDueByOwnerUserIdAndWorkspaceId(
                context.ownerUserId(), context.workspaceId(), now
        );

        return new StudyReviewStatisticsResponse(
                dueCount,
                todayReviews.size(),
                percentage(successfulToday, todayReviews.size()),
                currentStreak(reviews, today),
                reviews.size(),
                percentage(failures, reviews.size()),
                IntStream.rangeClosed(0, 5)
                        .mapToObj(score -> new StudyReviewStatisticsResponse.ScoreBucket(
                                score,
                                reviews.stream().filter(review -> review.score() == score).count()
                        ))
                        .toList(),
                maturity(cards),
                now
        );
    }

    private int currentStreak(List<StudyFlashcardReview> reviews, LocalDate today) {
        Set<LocalDate> activeDates = new HashSet<>();
        reviews.forEach(review -> activeDates.add(toStudyDate(review.reviewedAt())));
        LocalDate cursor = activeDates.contains(today) ? today : today.minusDays(1);
        int streak = 0;
        while (activeDates.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    private StudyReviewStatisticsResponse.MaturitySummary maturity(List<StudyFlashcard> cards) {
        long newCount = cards.stream().filter(card -> card.status() == StudyFlashcardStatus.NEW).count();
        long learningCount = cards.stream().filter(card -> card.status() == StudyFlashcardStatus.LEARNING).count();
        long suspendedCount = cards.stream().filter(card -> card.status() == StudyFlashcardStatus.SUSPENDED).count();
        long matureCount = cards.stream()
                .filter(card -> card.status() == StudyFlashcardStatus.REVIEW)
                .filter(card -> card.intervalDays() >= MATURE_INTERVAL_DAYS)
                .count();
        long youngCount = cards.stream()
                .filter(card -> card.status() == StudyFlashcardStatus.REVIEW)
                .filter(card -> card.intervalDays() < MATURE_INTERVAL_DAYS)
                .count();
        return new StudyReviewStatisticsResponse.MaturitySummary(
                newCount, learningCount, youngCount, matureCount, suspendedCount
        );
    }

    private LocalDate toStudyDate(OffsetDateTime time) {
        return time.atZoneSameInstant(studyZone).toLocalDate();
    }

    private double percentage(long numerator, long denominator) {
        if (denominator == 0) {
            return 0;
        }
        return Math.round(numerator * 10000.0 / denominator) / 100.0;
    }
}
