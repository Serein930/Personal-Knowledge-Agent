package com.agentmind.agent.confirmation.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agentmind.agent.audit.repository.AgentToolCallAuditRepository;
import com.agentmind.agent.confirmation.service.AgentWriteToolTransactionBoundary;
import com.agentmind.agent.confirmation.service.JdbcAgentWriteToolTransactionBoundary;
import com.agentmind.study.flashcard.repository.JdbcStudyFlashcardRepository;
import com.agentmind.study.flashcard.repository.StudyFlashcardRepository;
import com.agentmind.study.note.repository.JdbcKnowledgeNoteRepository;
import com.agentmind.study.note.repository.KnowledgeNoteRepository;
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
import org.springframework.jdbc.datasource.init.ScriptUtils;
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
        "spring.datasource.url=jdbc:postgresql://localhost:5432/agentmind",
        "spring.datasource.username=agentmind",
        "spring.datasource.password=agentmind_dev_password"
})
@AutoConfigureMockMvc
class JdbcAgentWriteToolIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DataSource dataSource;

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
    void setUpSchema() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/schema/agent_write_tools.sql"));
        }
        jdbcTemplate.update("delete from agent_tool_confirmations");
        jdbcTemplate.update("delete from agent_tool_call_audits");
        jdbcTemplate.update("delete from knowledge_notes");
        jdbcTemplate.update("delete from study_flashcards");
    }

    @Test
    void jdbcAdaptersShouldPersistConfirmationAuditAndIdempotentFlashcard() throws Exception {
        assertThat(transactionBoundary).isInstanceOf(JdbcAgentWriteToolTransactionBoundary.class);
        assertThat(confirmationRepository).isInstanceOf(JdbcAgentToolConfirmationRepository.class);
        assertThat(noteRepository).isInstanceOf(JdbcKnowledgeNoteRepository.class);
        assertThat(flashcardRepository).isInstanceOf(JdbcStudyFlashcardRepository.class);
        assertThat(auditRepository.getClass().getSimpleName()).isEqualTo("JdbcAgentToolCallAuditRepository");

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
    }
}
