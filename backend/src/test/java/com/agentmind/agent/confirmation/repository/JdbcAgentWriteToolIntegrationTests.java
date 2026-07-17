package com.agentmind.agent.confirmation.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agentmind.agent.audit.repository.AgentToolCallAuditRepository;
import com.agentmind.agent.audit.model.AgentToolType;
import com.agentmind.agent.confirmation.model.AgentToolConfirmation;
import com.agentmind.agent.confirmation.model.AgentToolConfirmationStatus;
import com.agentmind.agent.confirmation.service.AgentWriteToolTransactionBoundary;
import com.agentmind.agent.confirmation.service.JdbcAgentWriteToolTransactionBoundary;
import com.agentmind.agent.tool.AgentTool;
import com.agentmind.agent.tool.model.AgentToolDefinition;
import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.agent.tool.model.AgentToolExecutionResult;
import com.agentmind.study.flashcard.repository.JdbcStudyFlashcardRepository;
import com.agentmind.study.flashcard.repository.StudyFlashcardRepository;
import com.agentmind.study.note.repository.JdbcKnowledgeNoteRepository;
import com.agentmind.study.note.repository.KnowledgeNoteRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.aop.support.AopUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 智能体写工具 PostgreSQL 手动集成测试。
 *
 * <p>只有设置 {@code AGENTMIND_AGENT_JDBC_INTEGRATION_TEST=true} 时运行。测试会初始化 Stage 7 表并清空其中数据，
 * 因此只能连接本地开发数据库，不能指向共享或生产数据库。</p>
 */
@Tag("postgresql")
@EnabledIfEnvironmentVariable(named = "AGENTMIND_AGENT_JDBC_INTEGRATION_TEST", matches = "true")
@SpringBootTest(properties = {
        "agentmind.agent.persistence.store=jdbc",
        "agentmind.vector-store.type=memory",
        "spring.datasource.url=${AGENTMIND_POSTGRES_JDBC_URL:jdbc:postgresql://localhost:5432/agentmind}",
        "spring.datasource.username=${AGENTMIND_POSTGRES_USERNAME:agentmind}",
        "spring.datasource.password=${AGENTMIND_POSTGRES_PASSWORD:agentmind_dev_password}"
})
@AutoConfigureMockMvc
@Import(JdbcAgentWriteToolIntegrationTests.FailingWriteToolConfiguration.class)
class JdbcAgentWriteToolIntegrationTests {

    private static final String FAILING_TOOL_NAME = "test.failure_write";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AgentWriteToolTransactionBoundary transactionBoundary;

    @Autowired
    private AgentToolConfirmationRepository confirmationRepository;

    @Autowired
    private AgentToolCallAuditRepository auditRepository;

    @Autowired
    private KnowledgeNoteRepository noteRepository;

    @Autowired
    private StudyFlashcardRepository flashcardRepository;

    @BeforeEach
    void setUpSchema() {
        // 集成测试只允许通过 Flyway 准备结构，防止测试脚本与生产迁移产生两套定义。
        flyway.migrate();
        jdbcTemplate.update("delete from agent_tool_confirmations");
        jdbcTemplate.update("delete from agent_tool_call_audits");
        jdbcTemplate.update("delete from knowledge_notes");
        jdbcTemplate.update("delete from daily_study_task_events");
        jdbcTemplate.update("delete from daily_study_task_cards");
        jdbcTemplate.update("delete from daily_study_tasks");
        jdbcTemplate.update("delete from study_review_session_items");
        jdbcTemplate.update("delete from study_review_sessions");
        jdbcTemplate.update("delete from daily_study_plans");
        jdbcTemplate.update("delete from conversation_learning_summaries");
        jdbcTemplate.update("delete from learning_topic_profiles");
        jdbcTemplate.update("delete from fsrs_user_profile_versions");
        jdbcTemplate.update("delete from study_flashcard_fsrs_states");
        jdbcTemplate.update("delete from fsrs_parameter_optimization_jobs");
        jdbcTemplate.update("delete from fsrs_user_profiles");
        // 复习记录通过外键关联卡片，必须先清理明细记录再清理卡片主表。
        jdbcTemplate.update("delete from study_flashcard_reviews");
        jdbcTemplate.update("delete from study_flashcards");
    }

    @Test
    void jdbcAdaptersShouldPersistConfirmationAuditAndIdempotentFlashcard() throws Exception {
        assertThat(transactionBoundary).isInstanceOf(JdbcAgentWriteToolTransactionBoundary.class);
        assertThat(confirmationRepository).isInstanceOf(JdbcAgentToolConfirmationRepository.class);
        assertThat(noteRepository).isInstanceOf(JdbcKnowledgeNoteRepository.class);
        assertThat(flashcardRepository).isInstanceOf(JdbcStudyFlashcardRepository.class);
        // 审计仓储带事务代理时运行时类名会包含 CGLIB 后缀，应检查代理背后的目标类型。
        assertThat(AopUtils.getTargetClass(auditRepository).getSimpleName())
                .isEqualTo("JdbcAgentToolCallAuditRepository");

        long workspaceId = 19_001L;
        String requestBody = """
                {
                  "toolName":"flashcard.create",
                  "requestId":"jdbc-flashcard-001",
                  "arguments":{
                    "question":"什么是写工具事务边界？",
                    "answer":"确认单、审计和业务写入必须在同一事务中完成。"
                  }
                }
                """;
        MvcResult created = mockMvc.perform(post(
                            "/api/v1/workspaces/{workspaceId}/agent/write-tool-confirmations", workspaceId
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(created.getResponse().getContentAsString()).path("data");
        long confirmationId = data.path("confirmation").path("id").asLong();
        String token = data.path("confirmationToken").asText();

        mockMvc.perform(post(
                            "/api/v1/workspaces/{workspaceId}/agent/write-tool-confirmations/{id}/confirm",
                            workspaceId,
                            confirmationId
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode().put("confirmationToken", token).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.confirmation.status", equalTo("SUCCEEDED")));

        assertThat(jdbcTemplate.queryForObject("select count(*) from study_flashcards", Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from agent_tool_call_audits where status = 'SUCCEEDED'", Long.class
        )).isEqualTo(1L);

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/flashcards", workspaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total", equalTo(1)));

        // 第二个确认式写工具验证计划、任务和卡片关联可以在 PostgreSQL 同一事务中提交。
        MvcResult planConfirmationCreated = mockMvc.perform(post(
                            "/api/v1/workspaces/{workspaceId}/agent/write-tool-confirmations", workspaceId
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "toolName":"study_plan.create",
                                  "requestId":"jdbc-study-plan-001",
                                  "arguments":{"planDate":"%s","dailyReviewTarget":10}
                                }
                                """.formatted(LocalDate.now())))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode planConfirmationData = objectMapper.readTree(
                planConfirmationCreated.getResponse().getContentAsString()
        ).path("data");
        mockMvc.perform(post(
                            "/api/v1/workspaces/{workspaceId}/agent/write-tool-confirmations/{id}/confirm",
                            workspaceId,
                            planConfirmationData.path("confirmation").path("id").asLong()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("confirmationToken", planConfirmationData.path("confirmationToken").asText())
                                .toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.confirmation.status", equalTo("SUCCEEDED")));

        assertThat(jdbcTemplate.queryForObject("select count(*) from daily_study_plans", Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("select count(*) from daily_study_tasks", Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("select count(*) from daily_study_task_cards", Long.class)).isEqualTo(1L);
    }

    @Test
    void twoRepositoryInstancesShouldAllowOnlyOneAtomicExecutionClaim() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        AgentToolConfirmation confirmation = confirmationRepository.save(new AgentToolConfirmation(
                null,
                1L,
                19_002L,
                31L,
                41L,
                "multi-instance-claim-001",
                "flashcard.create",
                objectMapper.createObjectNode().put("question", "并发确认").put("answer", "只能成功一次"),
                "参数字段：question、answer",
                "test-token-digest",
                AgentToolConfirmationStatus.PENDING_CONFIRMATION,
                null,
                null,
                now,
                now.plusMinutes(5),
                now,
                null
        ));
        JdbcAgentToolConfirmationRepository firstInstance =
                new JdbcAgentToolConfirmationRepository(jdbcTemplate, objectMapper);
        JdbcAgentToolConfirmationRepository secondInstance =
                new JdbcAgentToolConfirmationRepository(jdbcTemplate, objectMapper);
        ExecutorService executor = Executors.newFixedThreadPool(12);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> attempts = new ArrayList<>();
        try {
            for (int index = 0; index < 24; index++) {
                JdbcAgentToolConfirmationRepository repository = index % 2 == 0 ? firstInstance : secondInstance;
                attempts.add(executor.submit(() -> {
                    start.await();
                    return repository.compareAndSetStatus(
                            confirmation.ownerUserId(),
                            confirmation.workspaceId(),
                            confirmation.id(),
                            AgentToolConfirmationStatus.PENDING_CONFIRMATION,
                            AgentToolConfirmationStatus.EXECUTING,
                            OffsetDateTime.now()
                    ).isPresent();
                }));
            }
            start.countDown();
            int successCount = 0;
            for (Future<Boolean> attempt : attempts) {
                if (attempt.get(10, TimeUnit.SECONDS)) {
                    successCount++;
                }
            }
            assertThat(successCount).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }

        assertThat(jdbcTemplate.queryForObject(
                "select status from agent_tool_confirmations where id = ?",
                String.class,
                confirmation.id()
        )).isEqualTo("EXECUTING");
    }

    @Test
    void failedWriteShouldRollbackBusinessDataAndCommitFailureAuditInNewTransaction() throws Exception {
        long workspaceId = 19_003L;
        MvcResult created = mockMvc.perform(post(
                            "/api/v1/workspaces/{workspaceId}/agent/write-tool-confirmations", workspaceId
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "toolName":"test.failure_write",
                                  "requestId":"jdbc-failure-001",
                                  "arguments":{"value":"触发事务回滚"}
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(created.getResponse().getContentAsString()).path("data");
        long confirmationId = data.path("confirmation").path("id").asLong();
        String token = data.path("confirmationToken").asText();

        mockMvc.perform(post(
                            "/api/v1/workspaces/{workspaceId}/agent/write-tool-confirmations/{id}/confirm",
                            workspaceId,
                            confirmationId
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode().put("confirmationToken", token).toString()))
                .andExpect(status().is5xxServerError());

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from knowledge_notes where request_id = 'jdbc-failure-001'",
                Long.class
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from agent_tool_call_audits "
                        + "where request_id = 'jdbc-failure-001' and status = 'FAILED'",
                Long.class
        )).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "select status from agent_tool_confirmations where id = ?",
                String.class,
                confirmationId
        )).isEqualTo("FAILED");
    }

    /**
     * 仅供数据库集成测试使用的故障写工具。它先写入业务表再抛出异常，用于证明外层事务确实回滚。
     */
    private static final class FailingWriteAgentTool implements AgentTool {

        private final JdbcTemplate jdbcTemplate;

        private FailingWriteAgentTool(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        @Override
        public AgentToolDefinition definition() {
            return new AgentToolDefinition(
                    FAILING_TOOL_NAME,
                    "验证失败审计独立事务",
                    AgentToolType.WRITE,
                    "{\"type\":\"object\"}"
            );
        }

        @Override
        public AgentToolExecutionResult execute(AgentToolExecutionContext context, JsonNode arguments) {
            OffsetDateTime now = OffsetDateTime.now();
            jdbcTemplate.update("""
                    insert into knowledge_notes (
                        owner_user_id, workspace_id, source_conversation_id, request_id,
                        title, content, created_at, updated_at
                    ) values (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    context.ownerUserId(),
                    context.workspaceId(),
                    context.conversationId(),
                    context.requestId(),
                    "不应提交的测试笔记",
                    arguments.path("value").asText(),
                    now,
                    now
            );
            throw new IllegalStateException("集成测试主动触发写工具失败");
        }
    }

    @TestConfiguration
    static class FailingWriteToolConfiguration {

        @Bean
        AgentTool failingWriteAgentTool(JdbcTemplate jdbcTemplate) {
            return new FailingWriteAgentTool(jdbcTemplate);
        }
    }
}
