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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", equalTo("SUCCEEDED")))
                .andReturn();
        return data(result).path("id").asLong();
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).path("data");
    }
}
