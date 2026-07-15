package com.agentmind.study.analytics.service;

import com.agentmind.agent.service.AgentToolExecutionAuthorizer;
import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.common.exception.BusinessException;
import com.agentmind.common.exception.ErrorCode;
import com.agentmind.study.analytics.model.dto.StudyTrendResponse;
import com.agentmind.study.flashcard.model.StudyFlashcardReview;
import com.agentmind.study.flashcard.repository.StudyFlashcardReviewRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 学习趋势聚合服务。
 *
 * <p>当前内存与 JDBC 模式共享同一聚合规则，保证接口语义一致。数据量增长后可以在 JDBC 适配器中
 * 下推为按日 SQL 投影，但正确率、日期时区和自然周边界仍以该服务定义为准。</p>
 */
@Service
public class StudyTrendApplicationService {

    private static final int MAX_RANGE_DAYS = 366;

    private final StudyFlashcardReviewRepository reviewRepository;
    private final AgentToolExecutionAuthorizer authorizer;
    private final ZoneId studyZone;

    public StudyTrendApplicationService(
            StudyFlashcardReviewRepository reviewRepository,
            AgentToolExecutionAuthorizer authorizer,
            @Value("${agentmind.study.time-zone:Asia/Shanghai}") String studyTimeZone
    ) {
        this.reviewRepository = reviewRepository;
        this.authorizer = authorizer;
        this.studyZone = ZoneId.of(studyTimeZone);
    }

    public StudyTrendResponse get(
            AgentToolExecutionContext context,
            LocalDate from,
            LocalDate to
    ) {
        authorizer.authorize(context);
        validateRange(from, to);
        List<StudyFlashcardReview> reviews = reviewRepository
                .findAllByOwnerUserIdAndWorkspaceId(context.ownerUserId(), context.workspaceId())
                .stream()
                .filter(review -> !studyDate(review).isBefore(from) && !studyDate(review).isAfter(to))
                .toList();

        Map<LocalDate, List<StudyFlashcardReview>> byDay = new LinkedHashMap<>();
        from.datesUntil(to.plusDays(1)).forEach(date -> byDay.put(date, new ArrayList<>()));
        reviews.forEach(review -> byDay.get(studyDate(review)).add(review));
        List<StudyTrendResponse.DailyPoint> daily = byDay.entrySet().stream()
                .map(entry -> dailyPoint(entry.getKey(), entry.getValue()))
                .toList();

        Map<LocalDate, List<StudyFlashcardReview>> byWeek = reviews.stream()
                .collect(Collectors.groupingBy(
                        review -> studyDate(review).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        List<StudyTrendResponse.WeeklyPoint> weekly = byWeek.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> weeklyPoint(entry.getKey(), entry.getValue(), from, to))
                .toList();

        long correct = reviews.stream().filter(review -> review.score() >= 3).count();
        long uniqueCards = reviews.stream().map(StudyFlashcardReview::flashcardId).distinct().count();
        int activeDays = (int) daily.stream().filter(point -> point.reviewCount() > 0).count();
        return new StudyTrendResponse(
                from, to, reviews.size(), uniqueCards, activeDays,
                percentage(correct, reviews.size()), daily, weekly
        );
    }

    private StudyTrendResponse.DailyPoint dailyPoint(
            LocalDate date,
            List<StudyFlashcardReview> reviews
    ) {
        long correct = reviews.stream().filter(review -> review.score() >= 3).count();
        long lapses = reviews.size() - correct;
        long uniqueCards = reviews.stream().map(StudyFlashcardReview::flashcardId).distinct().count();
        return new StudyTrendResponse.DailyPoint(
                date, reviews.size(), uniqueCards, correct, lapses, percentage(correct, reviews.size())
        );
    }

    private StudyTrendResponse.WeeklyPoint weeklyPoint(
            LocalDate weekStart,
            List<StudyFlashcardReview> reviews,
            LocalDate queryFrom,
            LocalDate queryTo
    ) {
        long correct = reviews.stream().filter(review -> review.score() >= 3).count();
        long uniqueCards = reviews.stream().map(StudyFlashcardReview::flashcardId).distinct().count();
        int activeDays = (int) reviews.stream().map(this::studyDate).distinct().count();
        LocalDate visibleStart = weekStart.isBefore(queryFrom) ? queryFrom : weekStart;
        LocalDate naturalEnd = weekStart.plusDays(6);
        LocalDate visibleEnd = naturalEnd.isAfter(queryTo) ? queryTo : naturalEnd;
        return new StudyTrendResponse.WeeklyPoint(
                visibleStart, visibleEnd, reviews.size(), uniqueCards, activeDays,
                correct, reviews.size() - correct, percentage(correct, reviews.size())
        );
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "趋势开始和结束日期不能为空");
        }
        if (from.isAfter(to)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "趋势开始日期不能晚于结束日期");
        }
        if (from.plusDays(MAX_RANGE_DAYS - 1L).isBefore(to)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "单次趋势查询不能超过366天");
        }
    }

    private LocalDate studyDate(StudyFlashcardReview review) {
        return review.reviewedAt().atZoneSameInstant(studyZone).toLocalDate();
    }

    private double percentage(long numerator, long denominator) {
        return denominator == 0 ? 0 : Math.round(numerator * 10000.0 / denominator) / 100.0;
    }
}
