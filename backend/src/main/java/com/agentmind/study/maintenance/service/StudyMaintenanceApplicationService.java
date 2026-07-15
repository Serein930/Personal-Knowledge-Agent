package com.agentmind.study.maintenance.service;

import com.agentmind.agent.service.AgentToolExecutionAuthorizer;
import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.common.exception.BusinessException;
import com.agentmind.common.exception.ErrorCode;
import com.agentmind.study.flashcard.fsrs.model.dto.StartFsrsOptimizationRequest;
import com.agentmind.study.flashcard.fsrs.repository.FsrsOptimizationJobRepository;
import com.agentmind.study.flashcard.fsrs.service.FsrsOptimizationApplicationService;
import com.agentmind.study.flashcard.model.StudyFlashcardReview;
import com.agentmind.study.flashcard.repository.StudyFlashcardReviewRepository;
import com.agentmind.study.maintenance.model.StudyDataScope;
import com.agentmind.study.maintenance.model.dto.StudyMaintenanceStatusResponse;
import com.agentmind.study.memory.service.ConversationLearningSummaryService;
import com.agentmind.study.plan.model.DailyStudyTask;
import com.agentmind.study.plan.repository.DailyStudyPlanRepository;
import com.agentmind.study.plan.service.DailyStudyTaskApplicationService;
import com.agentmind.study.profile.model.LearningTopicProfile;
import com.agentmind.study.profile.service.LearningProfileApplicationService;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 后台优化、画像刷新和任务补偿的统一编排服务。 */
@Service
public class StudyMaintenanceApplicationService {

    private final StudyFlashcardReviewRepository reviewRepository;
    private final FsrsOptimizationJobRepository optimizationJobRepository;
    private final FsrsOptimizationApplicationService optimizationService;
    private final LearningProfileApplicationService profileService;
    private final ConversationLearningSummaryService summaryService;
    private final DailyStudyPlanRepository planRepository;
    private final DailyStudyTaskApplicationService taskService;
    private final AgentToolExecutionAuthorizer authorizer;
    private final StudyMaintenanceMonitor monitor;
    private final ZoneId studyZone;
    private final Duration minimumOptimizationInterval;
    private final int batchSize;

    public StudyMaintenanceApplicationService(
            StudyFlashcardReviewRepository reviewRepository,
            FsrsOptimizationJobRepository optimizationJobRepository,
            FsrsOptimizationApplicationService optimizationService,
            LearningProfileApplicationService profileService,
            ConversationLearningSummaryService summaryService,
            DailyStudyPlanRepository planRepository,
            DailyStudyTaskApplicationService taskService,
            AgentToolExecutionAuthorizer authorizer,
            StudyMaintenanceMonitor monitor,
            @Value("${agentmind.study.time-zone:Asia/Shanghai}") String studyTimeZone,
            @Value("${agentmind.study.maintenance.minimum-optimization-interval:7d}") Duration minimumOptimizationInterval,
            @Value("${agentmind.study.maintenance.batch-size:100}") int batchSize
    ) {
        this.reviewRepository = reviewRepository;
        this.optimizationJobRepository = optimizationJobRepository;
        this.optimizationService = optimizationService;
        this.profileService = profileService;
        this.summaryService = summaryService;
        this.planRepository = planRepository;
        this.taskService = taskService;
        this.authorizer = authorizer;
        this.monitor = monitor;
        this.studyZone = ZoneId.of(studyTimeZone);
        this.minimumOptimizationInterval = minimumOptimizationInterval;
        this.batchSize = batchSize;
    }

    public StudyMaintenanceStatusResponse runNow(AgentToolExecutionContext context) {
        authorizer.authorize(context);
        if (!monitor.tryStart()) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "学习维护任务正在运行");
        }
        try {
            processScope(new StudyDataScope(context.ownerUserId(), context.workspaceId()));
        } finally {
            monitor.complete();
        }
        return monitor.snapshot();
    }

    public void runScheduled() {
        if (!monitor.tryStart()) {
            return;
        }
        try {
            List<StudyDataScope> scopes = reviewRepository.findActiveScopes(batchSize);
            for (StudyDataScope scope : scopes) {
                try {
                    processScope(scope);
                } catch (RuntimeException exception) {
                    monitor.failed(exception);
                }
            }
            compensatePendingTasks();
        } finally {
            monitor.complete();
        }
    }

    public StudyMaintenanceStatusResponse status(AgentToolExecutionContext context) {
        authorizer.authorize(context);
        return monitor.snapshot();
    }

    private void processScope(StudyDataScope scope) {
        AgentToolExecutionContext context = new AgentToolExecutionContext(
                scope.ownerUserId(), scope.workspaceId(), null
        );
        List<LearningTopicProfile> profiles = profileService.refreshInternal(scope.ownerUserId(), scope.workspaceId());
        summaryService.refreshInternal(context, profiles.stream().map(LearningTopicProfile::topic).toList());
        if (optimizationDue(scope.ownerUserId())) {
            optimizationService.startInternal(scope.ownerUserId(), new StartFsrsOptimizationRequest(false));
            monitor.optimizationCreated();
        }
        compensateScopeTasks(scope);
        monitor.scopeProcessed();
    }

    private boolean optimizationDue(Long ownerUserId) {
        return optimizationJobRepository.findLatestByOwnerUserId(ownerUserId)
                .map(job -> job.createdAt().plus(minimumOptimizationInterval).isBefore(OffsetDateTime.now()))
                .orElse(true);
    }

    private void compensatePendingTasks() {
        LocalDate today = LocalDate.now(studyZone);
        planRepository.findPendingTasksScheduledOnOrBefore(today, batchSize).forEach(task -> {
            try {
                compensateTask(task, today);
            } catch (RuntimeException exception) {
                monitor.failed(exception);
            }
        });
    }

    private void compensateScopeTasks(StudyDataScope scope) {
        LocalDate today = LocalDate.now(studyZone);
        planRepository.findPendingTasksScheduledOnOrBefore(today, batchSize).stream()
                .filter(task -> scope.ownerUserId().equals(task.ownerUserId()))
                .filter(task -> scope.workspaceId().equals(task.workspaceId()))
                .forEach(task -> compensateTask(task, today));
    }

    private void compensateTask(DailyStudyTask task, LocalDate today) {
        Set<Long> reviewedCardIds = reviewRepository.findAllByOwnerUserIdAndWorkspaceId(
                        task.ownerUserId(), task.workspaceId()
                ).stream()
                .filter(review -> studyDate(review).equals(task.scheduledDate()))
                .map(StudyFlashcardReview::flashcardId)
                .collect(Collectors.toSet());
        long completed = task.flashcardIds().stream().filter(reviewedCardIds::contains).count();
        if (completed >= task.targetCardCount()) {
            if (taskService.compensateCompletedTask(task, "根据真实复习记录补记任务完成")) {
                monitor.taskCompensated();
            }
        } else if (task.scheduledDate().isBefore(today)) {
            if (taskService.compensateRescheduledTask(task, today, "后台补偿逾期未完成任务")) {
                monitor.taskRescheduled();
            }
        }
    }

    private LocalDate studyDate(StudyFlashcardReview review) {
        return review.reviewedAt().atZoneSameInstant(studyZone).toLocalDate();
    }
}
