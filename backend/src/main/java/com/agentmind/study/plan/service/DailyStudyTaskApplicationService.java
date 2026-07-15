package com.agentmind.study.plan.service;

import com.agentmind.agent.service.AgentToolExecutionAuthorizer;
import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.common.exception.BusinessException;
import com.agentmind.common.exception.ErrorCode;
import com.agentmind.study.flashcard.model.StudyFlashcardReview;
import com.agentmind.study.flashcard.repository.StudyFlashcardReviewRepository;
import com.agentmind.study.plan.model.DailyStudyTask;
import com.agentmind.study.plan.model.DailyStudyTaskAction;
import com.agentmind.study.plan.model.DailyStudyTaskEvent;
import com.agentmind.study.plan.model.DailyStudyTaskStatus;
import com.agentmind.study.plan.model.dto.DailyStudyTaskEventResponse;
import com.agentmind.study.plan.model.dto.DailyStudyTaskResponse;
import com.agentmind.study.plan.model.dto.RescheduleDailyStudyTaskRequest;
import com.agentmind.study.plan.model.dto.SubmitDailyStudyTaskFeedbackRequest;
import com.agentmind.study.plan.model.dto.UpdateDailyStudyTaskRequest;
import com.agentmind.study.plan.repository.DailyStudyPlanRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 学习任务状态机、乐观锁和事件审计服务。 */
@Service
public class DailyStudyTaskApplicationService {

    private final DailyStudyPlanRepository repository;
    private final StudyFlashcardReviewRepository reviewRepository;
    private final AgentToolExecutionAuthorizer authorizer;
    private final ZoneId studyZone;

    public DailyStudyTaskApplicationService(
            DailyStudyPlanRepository repository,
            StudyFlashcardReviewRepository reviewRepository,
            AgentToolExecutionAuthorizer authorizer,
            @Value("${agentmind.study.time-zone:Asia/Shanghai}") String studyTimeZone
    ) {
        this.repository = repository;
        this.reviewRepository = reviewRepository;
        this.authorizer = authorizer;
        this.studyZone = ZoneId.of(studyTimeZone);
    }

    @Transactional
    public DailyStudyTaskResponse complete(
            AgentToolExecutionContext context,
            Long taskId,
            UpdateDailyStudyTaskRequest request
    ) {
        return transition(context, taskId, request.expectedVersion(), DailyStudyTaskStatus.COMPLETED,
                DailyStudyTaskAction.COMPLETED, request.comment());
    }

    @Transactional
    public DailyStudyTaskResponse skip(
            AgentToolExecutionContext context,
            Long taskId,
            UpdateDailyStudyTaskRequest request
    ) {
        return transition(context, taskId, request.expectedVersion(), DailyStudyTaskStatus.SKIPPED,
                DailyStudyTaskAction.SKIPPED, request.comment());
    }

    @Transactional
    public DailyStudyTaskResponse reschedule(
            AgentToolExecutionContext context,
            Long taskId,
            RescheduleDailyStudyTaskRequest request
    ) {
        authorizer.authorize(context);
        LocalDate today = LocalDate.now(studyZone);
        if (request.targetDate().isBefore(today)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "学习任务不能重新安排到过去日期");
        }
        DailyStudyTask current = requirePending(context, taskId);
        OffsetDateTime now = OffsetDateTime.now();
        DailyStudyTask changed = copy(
                current, DailyStudyTaskStatus.PENDING, request.targetDate(), current.feedbackScore(),
                current.feedbackComment(), null, null, current.version() + 1, now
        );
        DailyStudyTask saved = updateOrConflict(changed, request.expectedVersion());
        saveEvent(current, saved, DailyStudyTaskAction.RESCHEDULED, null, request.comment(), now);
        return toResponse(saved);
    }

    @Transactional
    public DailyStudyTaskResponse feedback(
            AgentToolExecutionContext context,
            Long taskId,
            SubmitDailyStudyTaskFeedbackRequest request
    ) {
        authorizer.authorize(context);
        DailyStudyTask current = require(context, taskId);
        OffsetDateTime now = OffsetDateTime.now();
        DailyStudyTask changed = copy(
                current, current.status(), current.scheduledDate(), request.score(), request.comment(),
                current.completedAt(), current.skippedAt(), current.version() + 1, now
        );
        DailyStudyTask saved = updateOrConflict(changed, request.expectedVersion());
        saveEvent(current, saved, DailyStudyTaskAction.FEEDBACK_RECORDED, request.score(), request.comment(), now);
        return toResponse(saved);
    }

    public List<DailyStudyTaskEventResponse> listEvents(AgentToolExecutionContext context, Long taskId) {
        authorizer.authorize(context);
        require(context, taskId);
        return repository.findTaskEvents(context.ownerUserId(), context.workspaceId(), taskId)
                .stream().map(this::toEventResponse).toList();
    }

    /** 后台补偿复用该入口，仍通过条件版本更新，不绕过任务状态机。 */
    @Transactional
    public boolean compensateCompletedTask(DailyStudyTask current, String reason) {
        if (current.status() != DailyStudyTaskStatus.PENDING) {
            return false;
        }
        OffsetDateTime now = OffsetDateTime.now();
        DailyStudyTask changed = copy(
                current, DailyStudyTaskStatus.COMPLETED, current.scheduledDate(),
                current.feedbackScore(), current.feedbackComment(), now, null,
                current.version() + 1, now
        );
        return repository.updateTask(changed, current.version()).map(saved -> {
            saveEvent(current, saved, DailyStudyTaskAction.COMPENSATED, null, reason, now);
            return true;
        }).orElse(false);
    }

    /** 后台逾期补偿入口：使用版本条件更新，并保留与手动改期相同的事件证据。 */
    @Transactional
    public boolean compensateRescheduledTask(DailyStudyTask current, LocalDate targetDate, String reason) {
        if (current.status() != DailyStudyTaskStatus.PENDING) {
            return false;
        }
        OffsetDateTime now = OffsetDateTime.now();
        DailyStudyTask changed = copy(
                current, DailyStudyTaskStatus.PENDING, targetDate,
                current.feedbackScore(), current.feedbackComment(), null, null,
                current.version() + 1, now
        );
        return repository.updateTask(changed, current.version()).map(saved -> {
            saveEvent(current, saved, DailyStudyTaskAction.RESCHEDULED, null, reason, now);
            return true;
        }).orElse(false);
    }

    private DailyStudyTaskResponse transition(
            AgentToolExecutionContext context,
            Long taskId,
            long expectedVersion,
            DailyStudyTaskStatus target,
            DailyStudyTaskAction action,
            String comment
    ) {
        authorizer.authorize(context);
        DailyStudyTask current = requirePending(context, taskId);
        OffsetDateTime now = OffsetDateTime.now();
        DailyStudyTask changed = copy(
                current, target, current.scheduledDate(), current.feedbackScore(), current.feedbackComment(),
                target == DailyStudyTaskStatus.COMPLETED ? now : null,
                target == DailyStudyTaskStatus.SKIPPED ? now : null,
                current.version() + 1, now
        );
        DailyStudyTask saved = updateOrConflict(changed, expectedVersion);
        saveEvent(current, saved, action, null, comment, now);
        return toResponse(saved);
    }

    private DailyStudyTask requirePending(AgentToolExecutionContext context, Long taskId) {
        DailyStudyTask task = require(context, taskId);
        if (task.status() != DailyStudyTaskStatus.PENDING) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "只有待执行任务可以完成、跳过或改期");
        }
        return task;
    }

    private DailyStudyTask require(AgentToolExecutionContext context, Long taskId) {
        return repository.findTaskByScopeAndId(context.ownerUserId(), context.workspaceId(), taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "学习任务不存在或无权访问"));
    }

    private DailyStudyTask updateOrConflict(DailyStudyTask task, long expectedVersion) {
        return repository.updateTask(task, expectedVersion)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_CONFLICT, "学习任务版本已变化，请刷新后重试"));
    }

    private void saveEvent(
            DailyStudyTask previous,
            DailyStudyTask next,
            DailyStudyTaskAction action,
            Integer feedbackScore,
            String comment,
            OffsetDateTime now
    ) {
        repository.saveTaskEvent(new DailyStudyTaskEvent(
                null, previous.id(), previous.ownerUserId(), previous.workspaceId(), action,
                previous.status(), next.status(), previous.scheduledDate(), next.scheduledDate(),
                feedbackScore, comment, now
        ));
    }

    private DailyStudyTask copy(
            DailyStudyTask source,
            DailyStudyTaskStatus status,
            LocalDate scheduledDate,
            Integer feedbackScore,
            String feedbackComment,
            OffsetDateTime completedAt,
            OffsetDateTime skippedAt,
            long version,
            OffsetDateTime updatedAt
    ) {
        return new DailyStudyTask(
                source.id(), source.planId(), source.ownerUserId(), source.workspaceId(),
                source.type(), source.priority(), status, scheduledDate, source.topic(),
                source.sourceDocumentId(), source.targetCardCount(), source.reason(), source.flashcardIds(),
                feedbackScore, feedbackComment, completedAt, skippedAt, version,
                source.createdAt(), updatedAt
        );
    }

    private DailyStudyTaskResponse toResponse(DailyStudyTask task) {
        Set<Long> reviewedIds = reviewRepository.findAllByOwnerUserIdAndWorkspaceId(
                        task.ownerUserId(), task.workspaceId()
                ).stream()
                .filter(review -> toStudyDate(review).equals(task.scheduledDate()))
                .map(StudyFlashcardReview::flashcardId)
                .collect(Collectors.toSet());
        long completedCards = task.flashcardIds().stream().filter(reviewedIds::contains).count();
        return new DailyStudyTaskResponse(
                task.id(), task.type(), task.priority(), task.status(), task.scheduledDate(),
                task.topic(), task.sourceDocumentId(), task.targetCardCount(), completedCards,
                task.status() == DailyStudyTaskStatus.COMPLETED || completedCards >= task.targetCardCount(),
                task.reason(), task.flashcardIds(), task.feedbackScore(), task.feedbackComment(),
                task.completedAt(), task.skippedAt(), task.version(), task.updatedAt()
        );
    }

    private LocalDate toStudyDate(StudyFlashcardReview review) {
        return review.reviewedAt().atZoneSameInstant(studyZone).toLocalDate();
    }

    private DailyStudyTaskEventResponse toEventResponse(DailyStudyTaskEvent event) {
        return new DailyStudyTaskEventResponse(
                event.id(), event.action(), event.previousStatus(), event.nextStatus(),
                event.previousScheduledDate(), event.nextScheduledDate(), event.feedbackScore(),
                event.comment(), event.createdAt()
        );
    }
}
