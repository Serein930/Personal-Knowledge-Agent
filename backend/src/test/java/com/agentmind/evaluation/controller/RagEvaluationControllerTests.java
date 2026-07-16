package com.agentmind.evaluation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agentmind.document.chunk.DocumentChunk;
import com.agentmind.knowledge.service.KnowledgeIndexingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockMultipartFile;

/**
 * 固定评估集与任务 API 全链路测试。
 *
 * <p>测试使用确定性向量和模拟回答，既不访问付费模型，也能稳定验证版本、指标、基线和知识空间边界。</p>
 */
@SpringBootTest(properties = {
        "agentmind.evaluation.input-cost-per-million-tokens=1.5",
        "agentmind.evaluation.output-cost-per-million-tokens=2.0"
})
@AutoConfigureMockMvc
class RagEvaluationControllerTests {

    private static final long WORKSPACE_ID = 98_101L;
    private static final String CHUNK_ID = "evaluation-virtual-thread-0";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private KnowledgeIndexingService indexingService;

    @BeforeEach
    void indexFixedKnowledge() {
        indexingService.indexChunks(WORKSPACE_ID, 7_001L, List.of(new DocumentChunk(
                CHUNK_ID, 7_001L, 0, "Java/虚拟线程",
                "虚拟线程适合大量阻塞式输入输出任务，并可降低平台线程占用。", 0, 31
        )));
    }

    @Test
    void shouldCreateVersionRunEvaluationCompareBaselineAndProtectWorkspace() throws Exception {
        long datasetId = createDataset();

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/evaluations/datasets/{datasetId}/versions",
                                WORKSPACE_ID, datasetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(datasetVersionJson("补充回归题")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version", equalTo(2)))
                .andExpect(jsonPath("$.data.cases.length()", equalTo(2)));

        long firstJobId = startJob(datasetId, 2, null);
        long secondJobId = startJob(datasetId, 2, firstJobId);

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/evaluations/jobs/{jobId}/comparison",
                                WORKSPACE_ID, secondJobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.comparable", equalTo(true)))
                .andExpect(jsonPath("$.data.baselineJobId", equalTo((int) firstJobId)))
                .andExpect(jsonPath("$.data.delta.recallAtK", equalTo(0.0)))
                .andExpect(jsonPath("$.data.caseDeltas.length()", equalTo(2)))
                .andExpect(jsonPath("$.data.caseDeltas[0].ndcgAtK", equalTo(0.0)));

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/evaluations/datasets/{datasetId}/trends",
                                WORKSPACE_ID, datasetId)
                        .param("version", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.points.length()", equalTo(2)))
                .andExpect(jsonPath("$.data.points[1].metrics.ndcgAtK", equalTo(100.0)));

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/evaluations/datasets/{datasetId}/versions/diff",
                                WORKSPACE_ID, datasetId)
                        .param("fromVersion", "1").param("toVersion", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unchanged", equalTo(2)));

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/evaluations/dashboard", WORKSPACE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.datasetCount", equalTo(1)))
                .andExpect(jsonPath("$.data.totalJobCount", equalTo(2)))
                .andExpect(jsonPath("$.data.latestSuccessfulJob.metrics.recallAtK", equalTo(100.0)))
                .andExpect(jsonPath("$.data.latestSuccessfulJob.metrics.meanReciprocalRank", equalTo(1.0)))
                .andExpect(jsonPath("$.data.latestSuccessfulJob.metrics.ndcgAtK", equalTo(100.0)))
                .andExpect(jsonPath("$.data.latestSuccessfulJob.metrics.citationCoverage", equalTo(100.0)))
                .andExpect(jsonPath("$.data.latestSuccessfulJob.metrics.refusalAccuracy", equalTo(100.0)))
                .andExpect(jsonPath("$.data.latestSuccessfulJob.metrics.totalTokens", greaterThan(0)))
                .andExpect(jsonPath("$.data.latestSuccessfulJob.metrics.estimatedCostUsd", greaterThan(0.0)));

        verifyJsonAndCsvExchange(datasetId);

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/evaluations/datasets/{datasetId}/versions",
                                WORKSPACE_ID + 1, datasetId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("RESOURCE_NOT_FOUND")));
    }

    private long createDataset() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/evaluations/datasets", WORKSPACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Java 并发固定回归集",
                                  "description": "验证检索、引用和拒答",
                                  "cases": %s
                                }
                                """.formatted(casesJson())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version", equalTo(1)))
                .andReturn();
        return readData(result).path("datasetId").asLong();
    }

    private long startJob(long datasetId, int version, Long expectedBaselineId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/evaluations/jobs", WORKSPACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "datasetId": %d,
                                  "datasetVersion": %d,
                                  "topK": 5,
                                  "experimentName": "固定向量实验",
                                  "retrievalStrategy": "VECTOR",
                                  "candidatePoolSize": 10,
                                  "rerankStrategy": "NONE",
                                  "qualityGate": {"minimumRecallAtK": 100, "minimumNdcgAtK": 100}
                                }
                                """.formatted(datasetId, version)))
                .andExpect(status().isAccepted())
                .andReturn();
        JsonNode submitted = readData(result);
        JsonNode data = awaitTerminal(submitted.path("id").asLong());
        assertThat(data.path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(data.path("metrics").path("caseCount").asInt()).isEqualTo(2);
        assertThat(data.path("metrics").path("refusalAccuracy").asDouble()).isEqualTo(100.0);
        assertThat(data.path("qualityGateResult").path("status").asText()).isEqualTo("PASSED");
        if (expectedBaselineId == null) {
            if (!data.path("baselineJobId").isMissingNode() && !data.path("baselineJobId").isNull()) {
                throw new AssertionError("首次运行不应关联基线任务");
            }
        } else if (data.path("baselineJobId").asLong() != expectedBaselineId) {
            throw new AssertionError("第二次运行未自动关联上一条成功任务");
        }
        return data.path("id").asLong();
    }

    private JsonNode awaitTerminal(long jobId) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            MvcResult result = mockMvc.perform(get(
                            "/api/v1/workspaces/{workspaceId}/evaluations/jobs/{jobId}", WORKSPACE_ID, jobId))
                    .andExpect(status().isOk()).andReturn();
            JsonNode data = readData(result);
            if (data.path("terminal").asBoolean()) {
                return data;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("评估任务未在预期时间内完成");
    }

    private void verifyJsonAndCsvExchange(long datasetId) throws Exception {
        for (String format : List.of("JSON", "CSV")) {
            MvcResult exported = mockMvc.perform(get(
                            "/api/v1/workspaces/{workspaceId}/evaluations/datasets/{datasetId}/versions/2/export",
                            WORKSPACE_ID, datasetId).param("format", format))
                    .andExpect(status().isOk()).andReturn();
            MockMultipartFile file = new MockMultipartFile(
                    "file", "evaluation." + format.toLowerCase(),
                    format.equals("JSON") ? MediaType.APPLICATION_JSON_VALUE : "text/csv",
                    exported.getResponse().getContentAsByteArray()
            );
            long targetWorkspace = WORKSPACE_ID + (format.equals("JSON") ? 10 : 20);
            mockMvc.perform(multipart(
                            "/api/v1/workspaces/{workspaceId}/evaluations/datasets/import", targetWorkspace)
                            .file(file).param("format", format))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.cases.length()", equalTo(2)));
        }
    }

    private String datasetVersionJson(String changeNote) {
        return """
                {"changeNote": "%s", "cases": %s}
                """.formatted(changeNote, casesJson());
    }

    private String casesJson() {
        return """
                [
                  {
                    "caseKey": "virtual-thread",
                    "question": "虚拟线程适合大量阻塞式输入输出任务",
                    "expectedRelevantChunkIds": ["%s"],
                    "expectedRelevantDocumentIds": [],
                    "expectedRefusal": false,
                    "expectedAnswerKeywords": ["虚拟线程", "阻塞"]
                  },
                  {
                    "caseKey": "unknown-topic",
                    "question": "量子引力弦论紫外完备性",
                    "expectedRelevantChunkIds": [],
                    "expectedRelevantDocumentIds": [],
                    "expectedRefusal": true,
                    "expectedAnswerKeywords": []
                  }
                ]
                """.formatted(CHUNK_ID);
    }

    private JsonNode readData(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).path("data");
    }
}
