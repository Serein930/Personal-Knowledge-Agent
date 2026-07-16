package com.agentmind.evaluation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentmind.agent.service.AgentToolExecutionAuthorizer;
import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.chat.config.RagAnswerGenerationProperties;
import com.agentmind.common.exception.BusinessException;
import com.agentmind.evaluation.config.RagEvaluationProperties;
import com.agentmind.evaluation.model.RagEvaluationCase;
import com.agentmind.evaluation.model.RagEvaluationDatasetVersion;
import com.agentmind.evaluation.model.RagEvaluationJobStatus;
import com.agentmind.evaluation.model.RagEvaluationRerankStrategy;
import com.agentmind.evaluation.model.RagEvaluationRetrievalStrategy;
import com.agentmind.evaluation.model.dto.RagEvaluationJobResponse;
import com.agentmind.evaluation.model.dto.StartRagEvaluationJobRequest;
import com.agentmind.evaluation.repository.InMemoryRagEvaluationJobRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 待执行任务取消、非法重试和按原实验快照重试测试。 */
class RagEvaluationJobServiceTests {

    private final AgentToolExecutionContext context = new AgentToolExecutionContext(1L, 2L, null);
    private final RagEvaluationDatasetService datasetService = mock(RagEvaluationDatasetService.class);
    private final RagEvaluationTaskDispatcher dispatcher = mock(RagEvaluationTaskDispatcher.class);
    private final InMemoryRagEvaluationJobRepository repository = new InMemoryRagEvaluationJobRepository();
    private RagEvaluationJobService service;

    @BeforeEach
    void setUp() {
        RagEvaluationDatasetVersion version = new RagEvaluationDatasetVersion(
                11L, 5L, 1L, 2L, 1, "固定版本",
                List.of(new RagEvaluationCase("refusal", "资料外问题", List.of(), List.of(), true, List.of())),
                OffsetDateTime.now()
        );
        when(datasetService.requireVersion(context, 5L, 1)).thenReturn(version);
        service = new RagEvaluationJobService(
                datasetService, repository, dispatcher, mock(AgentToolExecutionAuthorizer.class),
                new RagAnswerGenerationProperties(), new RagEvaluationProperties()
        );
    }

    @Test
    void shouldCancelPendingJobAndRetryWithFrozenExperimentConfiguration() {
        RagEvaluationJobResponse pending = service.start(context, request());
        assertThat(pending.status()).isEqualTo(RagEvaluationJobStatus.PENDING);
        verify(dispatcher).dispatch(1L, 2L, pending.id());

        RagEvaluationJobResponse canceled = service.cancel(context, pending.id());
        assertThat(canceled.status()).isEqualTo(RagEvaluationJobStatus.CANCELED);
        assertThat(canceled.terminal()).isTrue();

        RagEvaluationJobResponse retry = service.retry(context, canceled.id());
        assertThat(retry.status()).isEqualTo(RagEvaluationJobStatus.PENDING);
        assertThat(retry.retryOfJobId()).isEqualTo(canceled.id());
        assertThat(retry.experimentConfig()).isEqualTo(canceled.experimentConfig());
        verify(dispatcher).dispatch(1L, 2L, retry.id());
    }

    @Test
    void shouldRejectRetryForNonTerminalJob() {
        RagEvaluationJobResponse pending = service.start(context, request());
        assertThatThrownBy(() -> service.retry(context, pending.id()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只有失败或已取消任务可以重试");
    }

    private StartRagEvaluationJobRequest request() {
        return new StartRagEvaluationJobRequest(
                5L, 1, 5, "可重复混合检索实验", RagEvaluationRetrievalStrategy.HYBRID,
                20, RagEvaluationRerankStrategy.LEXICAL, null
        );
    }
}
