package com.agentmind.study.plan.service;

import com.agentmind.agent.service.AgentToolExecutionAuthorizer;
import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.common.exception.BusinessException;
import com.agentmind.common.exception.ErrorCode;
import com.agentmind.study.flashcard.model.StudyFlashcardReview;
import com.agentmind.study.flashcard.model.StudyFlashcard;
import com.agentmind.study.flashcard.repository.StudyFlashcardRepository;
import com.agentmind.study.flashcard.repository.StudyFlashcardReviewRepository;
import com.agentmind.study.plan.model.DailyStudyPlan;
import com.agentmind.study.plan.model.DailyStudyTask;
import com.agentmind.study.plan.model.dto.DailyStudyPlanResponse;
import com.agentmind.study.plan.model.dto.DailyStudyTaskResponse;
import com.agentmind.study.plan.model.dto.SaveDailyStudyPlanRequest;
import com.agentmind.study.plan.repository.DailyStudyPlanRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
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
    private final DailyStudyTaskGenerator taskGenerator;
    private final ZoneId studyZone;

    public DailyStudyPlanApplicationService(
            DailyStudyPlanRepository planRepository,
            StudyFlashcardRepository flashcardRepository,
            StudyFlashcardReviewRepository reviewRepository,
            AgentToolExecutionAuthorizer authorizer,
            DailyStudyTaskGenerator taskGenerator,
            @Value("${agentmind.study.time-zone:Asia/Shanghai}") String studyTimeZone
    ) {
        this.planRepository = planRepository;
        this.flashcardRepository = flashcardRepository;
        this.reviewRepository = reviewRepository;
        this.authorizer = authorizer;
        this.taskGenerator = taskGenerator;
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
        List<StudyFlashcard> cards = flashcardRepository.findAllByOwnerUserIdAndWorkspaceId(
                context.ownerUserId(), context.workspaceId()
        );
        long dueSnapshot = flashcardRepository.countDueByOwnerUserIdAndWorkspaceId(
                context.ownerUserId(), context.workspaceId(), planDeadline
        );
        List<DailyStudyTask> tasks = taskGenerator.generate(
                context.ownerUserId(), context.workspaceId(), cards, request, planDeadline, now
        );
        DailyStudyPlan saved = planRepository.saveOrUpdate(new DailyStudyPlan(
                null, context.ownerUserId(), context.workspaceId(), request.planDate(),
                request.dailyReviewTarget(), Math.toIntExact(dueSnapshot), now, now
        ), tasks);
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
        List<StudyFlashcardReview> reviews = reviewRepository.findAllByOwnerUserIdAndWorkspaceId(
                        plan.ownerUserId(), plan.workspaceId()
                ).stream()
                .filter(review -> toStudyDate(review).equals(plan.planDate()))
                .toList();
        Set<Long> completedFlashcardIds = reviews.stream()
                .map(StudyFlashcardReview::flashcardId)
                .collect(Collectors.toSet());
        long completed = completedFlashcardIds.size();
        long remaining = Math.max(0, plan.dailyReviewTarget() - completed);
        double progress = Math.min(100, Math.round(completed * 10000.0 / plan.dailyReviewTarget()) / 100.0);
        List<DailyStudyTaskResponse> tasks = planRepository.findTasksByScopeAndPlanId(
                        plan.ownerUserId(), plan.workspaceId(), plan.id()
                ).stream()
                .map(task -> toTaskResponse(task, completedFlashcardIds))
                .toList();
        return new DailyStudyPlanResponse(
                plan.id(), plan.workspaceId(), plan.planDate(), plan.dailyReviewTarget(),
                plan.dueCardSnapshot(), completed, remaining, progress,
                completed >= plan.dailyReviewTarget(), tasks, plan.updatedAt()
        );
    }

    private DailyStudyTaskResponse toTaskResponse(DailyStudyTask task, Set<Long> completedFlashcardIds) {
        long completed = task.flashcardIds().stream().filter(completedFlashcardIds::contains).count();
        return new DailyStudyTaskResponse(
                task.id(), task.type(), task.priority(), task.topic(), task.sourceDocumentId(),
                task.targetCardCount(), completed, completed >= task.targetCardCount(),
                task.reason(), task.flashcardIds()
        );
    }

    private LocalDate toStudyDate(StudyFlashcardReview review) {
        return review.reviewedAt().atZoneSameInstant(studyZone).toLocalDate();
    }
}
