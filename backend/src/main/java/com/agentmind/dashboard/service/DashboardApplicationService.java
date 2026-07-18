package com.agentmind.dashboard.service;

import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.chat.model.RagModelCallMetricAggregate;
import com.agentmind.chat.repository.RagModelCallObservationRepository;
import com.agentmind.dashboard.model.dto.DashboardOverviewResponse;
import com.agentmind.document.model.DocumentMetadata;
import com.agentmind.document.model.IngestionStatus;
import com.agentmind.document.model.dto.DocumentSummaryResponse;
import com.agentmind.document.repository.DocumentMetadataRepository;
import com.agentmind.study.flashcard.model.dto.StudyReviewStatisticsResponse;
import com.agentmind.study.flashcard.service.StudyReviewStatisticsService;
import com.agentmind.study.plan.model.dto.DailyStudyPlanResponse;
import com.agentmind.study.plan.model.dto.DailyStudyTaskResponse;
import com.agentmind.study.plan.service.DailyStudyPlanApplicationService;
import com.agentmind.workspace.model.KnowledgeWorkspace;
import com.agentmind.workspace.repository.KnowledgeWorkspaceRepository;
import com.agentmind.workspace.service.WorkspaceAccessService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 工作台查询应用服务。
 *
 * <p>该服务只编排已有业务模块的查询能力，不在此处重新实现文档摄取、学习统计或模型审计规则。
 * 整个查询首先执行知识空间成员校验，后续数据读取始终限定在当前知识空间内。</p>
 */
@Service
public class DashboardApplicationService {

    private static final int RECENT_DOCUMENT_LIMIT = 5;

    private final WorkspaceAccessService workspaceAccessService;
    private final KnowledgeWorkspaceRepository workspaceRepository;
    private final DocumentMetadataRepository documentRepository;
    private final StudyReviewStatisticsService studyStatisticsService;
    private final DailyStudyPlanApplicationService studyPlanService;
    private final RagModelCallObservationRepository observationRepository;
    private final ZoneId studyZone;

    public DashboardApplicationService(
            WorkspaceAccessService workspaceAccessService,
            KnowledgeWorkspaceRepository workspaceRepository,
            DocumentMetadataRepository documentRepository,
            StudyReviewStatisticsService studyStatisticsService,
            DailyStudyPlanApplicationService studyPlanService,
            RagModelCallObservationRepository observationRepository,
            @Value("${agentmind.study.time-zone:Asia/Shanghai}") String studyTimeZone
    ) {
        this.workspaceAccessService = workspaceAccessService;
        this.workspaceRepository = workspaceRepository;
        this.documentRepository = documentRepository;
        this.studyStatisticsService = studyStatisticsService;
        this.studyPlanService = studyPlanService;
        this.observationRepository = observationRepository;
        this.studyZone = ZoneId.of(studyTimeZone);
    }

    /**
     * 汇总当前用户在指定知识空间中的首屏数据。
     *
     * <p>只读事务让 JDBC 仓储在同一个一致性视图中完成查询；内存仓储模式下该注解不会改变行为。</p>
     */
    @Transactional(readOnly = true)
    public DashboardOverviewResponse getOverview(Long ownerUserId, Long workspaceId) {
        workspaceAccessService.requireReadable(ownerUserId, workspaceId);

        OffsetDateTime now = OffsetDateTime.now();
        LocalDate today = now.atZoneSameInstant(studyZone).toLocalDate();
        AgentToolExecutionContext context = new AgentToolExecutionContext(ownerUserId, workspaceId, null);
        List<DocumentMetadata> documents = documentRepository.findAllByWorkspaceId(workspaceId);
        StudyReviewStatisticsResponse studyStatistics = studyStatisticsService.summarize(context);
        DailyStudyPlanResponse todayPlan = studyPlanService.find(context, today).orElse(null);
        List<RagModelCallMetricAggregate> modelMetrics = observationRepository
                .aggregateMetricsByWorkspaceId(workspaceId);

        long modelCallCount = modelMetrics.stream()
                .mapToLong(RagModelCallMetricAggregate::totalCallCount)
                .sum();
        long totalModelLatency = modelMetrics.stream()
                .mapToLong(RagModelCallMetricAggregate::totalElapsedMillis)
                .sum();
        List<DailyStudyTaskResponse> studyTasks = todayPlan == null ? List.of() : todayPlan.tasks();

        return new DashboardOverviewResponse(
                documents.size(),
                documents.stream().filter(document -> isSameStudyDate(document.createdAt(), today)).count(),
                documents.stream().filter(this::isPendingIngestion).count(),
                studyTasks.size(),
                studyStatistics.dueCount(),
                modelCallCount,
                modelCallCount == 0 ? 0 : Math.round((double) totalModelLatency / modelCallCount),
                recentDocuments(documents, workspaceId),
                studyTasks,
                now
        );
    }

    private List<DocumentSummaryResponse> recentDocuments(List<DocumentMetadata> documents, Long workspaceId) {
        String workspaceName = workspaceRepository.findById(workspaceId)
                .map(KnowledgeWorkspace::getName)
                .orElse("未知知识空间");
        return documents.stream()
                .sorted(Comparator.comparing(DocumentMetadata::updatedAt).reversed())
                .limit(RECENT_DOCUMENT_LIMIT)
                .map(document -> new DocumentSummaryResponse(
                        document.id(),
                        document.title(),
                        document.sourceType(),
                        document.workspaceId(),
                        workspaceName,
                        document.tags(),
                        document.ingestionStatus(),
                        document.chunkCount(),
                        document.updatedAt()
                ))
                .toList();
    }

    private boolean isPendingIngestion(DocumentMetadata document) {
        return document.ingestionStatus() == IngestionStatus.PENDING
                || document.ingestionStatus() == IngestionStatus.RUNNING;
    }

    private boolean isSameStudyDate(OffsetDateTime time, LocalDate expectedDate) {
        return time.atZoneSameInstant(studyZone).toLocalDate().equals(expectedDate);
    }
}
