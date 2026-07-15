package com.agentmind.study.plan.service;

import com.agentmind.agent.service.AgentToolExecutionAuthorizer;
import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.common.exception.BusinessException;
import com.agentmind.common.exception.ErrorCode;
import com.agentmind.study.flashcard.model.StudyFlashcardReview;
import com.agentmind.study.flashcard.repository.StudyFlashcardRepository;
import com.agentmind.study.flashcard.repository.StudyFlashcardReviewRepository;
import com.agentmind.study.plan.model.DailyStudyPlan;
import com.agentmind.study.plan.model.dto.DailyStudyPlanResponse;
import com.agentmind.study.plan.model.dto.SaveDailyStudyPlanRequest;
import com.agentmind.study.plan.repository.DailyStudyPlanRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 每日学习计划应用服务。
 */
@Service
public class DailyStudyPlanApplicationService {

    private final DailyStudyPlanRepository planRepository;
    private final StudyFlashcardRepository flashcardRepository;
    private final StudyFlashcardReviewRepository reviewRepository;
    private final AgentToolExecutionAuthorizer authorizer;
    private final ZoneId studyZone;

    public DailyStudyPlanApplicationService(
            DailyStudyPlanRepository planRepository,
            StudyFlashcardRepository flashcardRepository,
            StudyFlashcardReviewRepository reviewRepository,
            AgentToolExecutionAuthorizer authorizer,
            @Value("${agentmind.study.time-zone:Asia/Shanghai}") String studyTimeZone
    ) {
        this.planRepository = planRepository;
        this.flashcardRepository = flashcardRepository;
        this.reviewRepository = reviewRepository;
        this.authorizer = authorizer;
        this.studyZone = ZoneId.of(studyTimeZone);
    }

    public DailyStudyPlanResponse save(
            AgentToolExecutionContext context,
            SaveDailyStudyPlanRequest request
    ) {
        authorizer.authorize(context);
        LocalDate today = LocalDate.now(studyZone);
        if (request.planDate().isBefore(today)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不能为过去日期创建学习计划");
        }
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime planDeadline = request.planDate().plusDays(1).atStartOfDay(studyZone).toOffsetDateTime();
        long dueSnapshot = flashcardRepository.countDueByOwnerUserIdAndWorkspaceId(
                context.ownerUserId(), context.workspaceId(), planDeadline
        );
        DailyStudyPlan saved = planRepository.saveOrUpdate(new DailyStudyPlan(
                null, context.ownerUserId(), context.workspaceId(), request.planDate(),
                request.dailyReviewTarget(), Math.toIntExact(dueSnapshot), now, now
        ));
        return toResponse(saved);
    }

    public DailyStudyPlanResponse get(
            AgentToolExecutionContext context,
            LocalDate planDate
    ) {
        authorizer.authorize(context);
        DailyStudyPlan plan = planRepository.findByScopeAndDate(
                        context.ownerUserId(), context.workspaceId(), planDate
                )
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "指定日期的学习计划不存在"
                ));
        return toResponse(plan);
    }

    private DailyStudyPlanResponse toResponse(DailyStudyPlan plan) {
        long completed = reviewRepository.findAllByOwnerUserIdAndWorkspaceId(
                        plan.ownerUserId(), plan.workspaceId()
                ).stream()
                .filter(review -> toStudyDate(review).equals(plan.planDate()))
                .count();
        long remaining = Math.max(0, plan.dailyReviewTarget() - completed);
        double progress = Math.min(100, Math.round(completed * 10000.0 / plan.dailyReviewTarget()) / 100.0);
        return new DailyStudyPlanResponse(
                plan.id(), plan.workspaceId(), plan.planDate(), plan.dailyReviewTarget(),
                plan.dueCardSnapshot(), completed, remaining, progress,
                completed >= plan.dailyReviewTarget(), plan.updatedAt()
        );
    }

    private LocalDate toStudyDate(StudyFlashcardReview review) {
        return review.reviewedAt().atZoneSameInstant(studyZone).toLocalDate();
    }
}
