package com.agentmind.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.agentmind.chat.model.RagModelCallMetricAggregate;
import com.agentmind.chat.repository.RagModelCallObservationRepository;
import com.agentmind.common.exception.BusinessException;
import com.agentmind.dashboard.model.dto.DashboardOverviewResponse;
import com.agentmind.document.model.DocumentMetadata;
import com.agentmind.document.model.DocumentSourceType;
import com.agentmind.document.model.IngestionStatus;
import com.agentmind.document.repository.DocumentMetadataRepository;
import com.agentmind.study.flashcard.model.dto.StudyReviewStatisticsResponse;
import com.agentmind.study.flashcard.service.StudyReviewStatisticsService;
import com.agentmind.study.plan.model.DailyStudyTaskPriority;
import com.agentmind.study.plan.model.DailyStudyTaskStatus;
import com.agentmind.study.plan.model.DailyStudyTaskType;
import com.agentmind.study.plan.model.dto.DailyStudyPlanResponse;
import com.agentmind.study.plan.model.dto.DailyStudyTaskResponse;
import com.agentmind.study.plan.service.DailyStudyPlanApplicationService;
import com.agentmind.workspace.model.KnowledgeWorkspace;
import com.agentmind.workspace.repository.KnowledgeWorkspaceRepository;
import com.agentmind.workspace.service.WorkspaceAccessService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 工作台聚合口径和权限边界单元测试。 */
class DashboardApplicationServiceTests {

    private final WorkspaceAccessService workspaceAccessService = mock(WorkspaceAccessService.class);
    private final KnowledgeWorkspaceRepository workspaceRepository = mock(KnowledgeWorkspaceRepository.class);
    private final DocumentMetadataRepository documentRepository = mock(DocumentMetadataRepository.class);
    private final StudyReviewStatisticsService studyStatisticsService = mock(StudyReviewStatisticsService.class);
    private final DailyStudyPlanApplicationService studyPlanService = mock(DailyStudyPlanApplicationService.class);
    private final RagModelCallObservationRepository observationRepository =
            mock(RagModelCallObservationRepository.class);

    private DashboardApplicationService service;

    @BeforeEach
    void setUp() {
        service = new DashboardApplicationService(
                workspaceAccessService,
                workspaceRepository,
                documentRepository,
                studyStatisticsService,
                studyPlanService,
                observationRepository,
                "Asia/Shanghai"
        );
    }

    @Test
    void shouldAggregateWorkspaceDataWithStableMetricDefinitions() {
        OffsetDateTime now = OffsetDateTime.now();
        KnowledgeWorkspace workspace = new KnowledgeWorkspace();
        workspace.setId(9L);
        workspace.setName("智能体工程学习");
        when(workspaceRepository.findById(9L)).thenReturn(Optional.of(workspace));
        when(documentRepository.findAllByWorkspaceId(9L)).thenReturn(List.of(
                document(1L, "较早文档", IngestionStatus.SUCCEEDED, now.minusDays(1)),
                document(2L, "今日处理中", IngestionStatus.RUNNING, now)
        ));
        when(studyStatisticsService.summarize(any())).thenReturn(statistics(7, now));
        when(studyPlanService.find(any(), any(LocalDate.class))).thenReturn(Optional.of(plan(now)));
        when(observationRepository.aggregateMetricsByWorkspaceId(9L)).thenReturn(List.of(
                new RagModelCallMetricAggregate("模型甲", "提示词一", 2, 2, 0, 0, 0, 300),
                new RagModelCallMetricAggregate("模型乙", "提示词二", 1, 1, 0, 0, 0, 600)
        ));

        DashboardOverviewResponse result = service.getOverview(3L, 9L);

        assertThat(result.knowledgeAssetCount()).isEqualTo(2);
        assertThat(result.ingestedToday()).isEqualTo(1);
        assertThat(result.pendingIngestionCount()).isEqualTo(1);
        assertThat(result.todayPlanTaskCount()).isEqualTo(1);
        assertThat(result.dueFlashcardCount()).isEqualTo(7);
        assertThat(result.agentCallCount()).isEqualTo(3);
        assertThat(result.averageAgentLatencyMillis()).isEqualTo(300);
        assertThat(result.recentDocuments()).extracting(document -> document.title())
                .containsExactly("今日处理中", "较早文档");
        assertThat(result.recentDocuments().getFirst().workspaceName()).isEqualTo("智能体工程学习");
        assertThat(result.studyTasks()).hasSize(1);
        verify(workspaceAccessService).requireReadable(3L, 9L);
    }

    @Test
    void shouldStopBeforeReadingDataWhenWorkspaceAccessIsDenied() {
        when(workspaceAccessService.requireReadable(8L, 99L))
                .thenThrow(new BusinessException(com.agentmind.common.exception.ErrorCode.FORBIDDEN, "无权访问"));

        assertThatThrownBy(() -> service.getOverview(8L, 99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权访问");

        verifyNoInteractions(
                workspaceRepository,
                documentRepository,
                studyStatisticsService,
                studyPlanService,
                observationRepository
        );
    }

    private DocumentMetadata document(
            Long id,
            String title,
            IngestionStatus status,
            OffsetDateTime createdAt
    ) {
        return new DocumentMetadata(
                id, 3L, 9L, title, DocumentSourceType.MARKDOWN,
                null, title + ".md", null, "text/markdown", 12, null,
                List.of("测试"), status, status == IngestionStatus.SUCCEEDED ? 2 : 0,
                createdAt, createdAt
        );
    }

    private StudyReviewStatisticsResponse statistics(long dueCount, OffsetDateTime generatedAt) {
        return new StudyReviewStatisticsResponse(
                dueCount, 0, 0, 0, 0, 0, List.of(),
                new StudyReviewStatisticsResponse.MaturitySummary(0, 0, 0, 0, 0),
                generatedAt
        );
    }

    private DailyStudyPlanResponse plan(OffsetDateTime updatedAt) {
        DailyStudyTaskResponse task = new DailyStudyTaskResponse(
                1L, DailyStudyTaskType.DUE_REVIEW, DailyStudyTaskPriority.HIGH,
                DailyStudyTaskStatus.PENDING, LocalDate.now(), "RAG", null,
                10, 3, false, "复习到期卡片", List.of(1L, 2L),
                null, null, null, null, 0, updatedAt
        );
        return new DailyStudyPlanResponse(
                1L, 9L, LocalDate.now(), 10, 7, 3, 7,
                30, false, List.of(task), updatedAt
        );
    }
}
