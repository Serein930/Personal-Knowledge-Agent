package com.agentmind.evaluation.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import com.agentmind.evaluation.model.RagEvaluationExperimentConfig;
import com.agentmind.evaluation.model.RagEvaluationJob;
import com.agentmind.evaluation.model.RagEvaluationJobStatus;
import com.agentmind.evaluation.model.RagEvaluationRerankStrategy;
import com.agentmind.evaluation.model.RagEvaluationRetrievalStrategy;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Stage 9 PostgreSQL 手动集成测试。
 *
 * <p>只有显式设置开关时运行。测试会清空本地评估表，验证评估集版本、JSONB 指标、逐题证据和基线编号
 * 能够跨请求从数据库恢复，禁止连接共享或生产数据库。</p>
 */
@Tag("postgresql")
@EnabledIfEnvironmentVariable(named = "AGENTMIND_EVALUATION_JDBC_INTEGRATION_TEST", matches = "true")
@SpringBootTest(properties = {
        "agentmind.evaluation.store=jdbc",
        "agentmind.evaluation.recovery-enabled=false",
        "agentmind.vector-store.type=memory",
        "spring.datasource.url=${AGENTMIND_POSTGRES_JDBC_URL:jdbc:postgresql://localhost:5432/agentmind}",
        "spring.datasource.username=${AGENTMIND_POSTGRES_USERNAME:agentmind}",
        "spring.datasource.password=${AGENTMIND_POSTGRES_PASSWORD:agentmind_dev_password}"
})
@AutoConfigureMockMvc
class JdbcRagEvaluationIntegrationTests {

    private static final long WORKSPACE_ID = 98_201L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RagEvaluationDatasetRepository datasetRepository;

    @Autowired
    private RagEvaluationJobRepository jobRepository;

    @BeforeEach
    void setUpSchema() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            new ResourceDatabasePopulator(new ClassPathResource("db/schema/rag_evaluations.sql"))
                    .populate(connection);
        }
        jdbcTemplate.update("delete from rag_evaluation_jobs");
        jdbcTemplate.update("delete from rag_evaluation_dataset_versions");
        jdbcTemplate.update("delete from rag_evaluation_datasets");
    }

    @Test
    void shouldPersistImmutableDatasetJsonMetricsEvidenceAndBaseline() throws Exception {
        assertThat(datasetRepository).isInstanceOf(JdbcRagEvaluationDatasetRepository.class);
        assertThat(jobRepository).isInstanceOf(JdbcRagEvaluationJobRepository.class);
        long datasetId = createDataset();
        long firstJobId = runJob(datasetId);
        long secondJobId = runJob(datasetId);

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/evaluations/jobs/{jobId}",
                                WORKSPACE_ID, secondJobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.baselineJobId", equalTo((int) firstJobId)))
                .andExpect(jsonPath("$.data.metrics.refusalAccuracy", equalTo(100.0)))
                .andExpect(jsonPath("$.data.caseResults[0].caseKey", equalTo("jdbc-refusal")));

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/evaluations/dashboard", WORKSPACE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.latestSuccessfulJob.id", equalTo((int) secondJobId)))
                .andExpect(jsonPath("$.data.latestComparison.comparable", equalTo(true)));

        assertThat(jdbcTemplate.queryForObject("select count(*) from rag_evaluation_datasets", Long.class))
                .isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("select count(*) from rag_evaluation_dataset_versions", Long.class))
                .isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("select count(*) from rag_evaluation_jobs", Long.class))
                .isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject(
                "select metrics ->> 'refusalAccuracy' from rag_evaluation_jobs where id = ?",
                String.class, secondJobId
        )).isEqualTo("100.0");
    }

    @Test
    void shouldClaimLeaseExclusivelyAndRecoverExpiredOwner() throws Exception {
        long datasetId = createDataset();
        Long ownerUserId = jdbcTemplate.queryForObject(
                "select owner_user_id from rag_evaluation_datasets where id = ?",
                Long.class,
                datasetId
        );
        OffsetDateTime now = OffsetDateTime.now();
        RagEvaluationExperimentConfig config = new RagEvaluationExperimentConfig(
                "PostgreSQL 租约测试", "markdown-aware-v1", RagEvaluationRetrievalStrategy.HYBRID,
                20, RagEvaluationRerankStrategy.NONE, 5, "rag-chat-v1", "mock-local"
        );
        RagEvaluationJob saved = jobRepository.save(new RagEvaluationJob(
                null, ownerUserId, WORKSPACE_ID, datasetId, 1, RagEvaluationJobStatus.PENDING,
                "HYBRID", 5, "rag-chat-v1", "mock-local", config,
                null, null, 1, 0, 0, null, null, null, List.of(), "",
                0, 0, "", null, null, now, null, now, null
        ));

        RagEvaluationJob claimed = jobRepository.claim(
                ownerUserId, WORKSPACE_ID, saved.id(), "jdbc-instance-a", now, now.plusSeconds(30)
        ).orElseThrow();

        assertThat(jobRepository.claim(
                ownerUserId, WORKSPACE_ID, saved.id(), "jdbc-instance-b", now, now.plusSeconds(30)
        )).isEmpty();
        assertThat(jobRepository.updateIfStatusAndLeaseOwner(
                claimed.withProgress(0, List.of()),
                java.util.Set.of(RagEvaluationJobStatus.RUNNING),
                "jdbc-instance-b",
                now
        )).isEmpty();

        RagEvaluationJob recovered = jobRepository.recoverExpiredLeases(now.plusSeconds(31), 10).getFirst();
        assertThat(recovered.status()).isEqualTo(RagEvaluationJobStatus.PENDING);
        assertThat(recovered.recoveryCount()).isEqualTo(1);
        assertThat(recovered.leaseOwner()).isEmpty();
    }

    private long createDataset() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/evaluations/datasets", WORKSPACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"PostgreSQL 固定评估集",
                                  "description":"验证 JSONB 持久化",
                                  "cases":[{
                                    "caseKey":"jdbc-refusal",
                                    "question":"知识库外的固定拒答题",
                                    "expectedRelevantChunkIds":[],
                                    "expectedRelevantDocumentIds":[],
                                    "expectedRefusal":true,
                                    "expectedAnswerKeywords":[]
                                  }]
                                }
                                """))
                .andExpect(status().isOk()).andReturn();
        return data(result).path("datasetId").asLong();
    }

    private long runJob(long datasetId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/evaluations/jobs", WORKSPACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"datasetId":%d,"datasetVersion":1,"topK":5}
                                """.formatted(datasetId)))
                .andExpect(status().isAccepted())
                .andReturn();
        long jobId = data(result).path("id").asLong();
        for (int attempt = 0; attempt < 100; attempt++) {
            MvcResult current = mockMvc.perform(get(
                            "/api/v1/workspaces/{workspaceId}/evaluations/jobs/{jobId}", WORKSPACE_ID, jobId))
                    .andExpect(status().isOk()).andReturn();
            JsonNode job = data(current);
            if (job.path("terminal").asBoolean()) {
                assertThat(job.path("status").asText()).isEqualTo("SUCCEEDED");
                return jobId;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("PostgreSQL 评估任务未在预期时间内完成");
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).path("data");
    }
}
