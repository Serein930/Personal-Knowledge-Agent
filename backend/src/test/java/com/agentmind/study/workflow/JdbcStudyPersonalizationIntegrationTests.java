package com.agentmind.study.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agentmind.chat.memory.model.ChatMessageRole;
import com.agentmind.chat.memory.model.ChatMessageStatus;
import com.agentmind.chat.memory.repository.ChatMemoryRepository;
import com.agentmind.study.flashcard.model.StudyFlashcard;
import com.agentmind.study.flashcard.model.StudyFlashcardStatus;
import com.agentmind.study.flashcard.repository.StudyFlashcardRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
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
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Stage 8 学习画像与长期记忆 PostgreSQL 全链路手动集成测试。
 *
 * <p>测试从 HTTP 接口进入，依次验证 FSRS 参数版本、学习画像、会话摘要、每日任务状态和
 * 不可变任务事件都真实写入 PostgreSQL。只有显式设置测试开关时才运行，避免常规构建依赖本地数据库。</p>
 */
@Tag("postgresql")
@EnabledIfEnvironmentVariable(named = "AGENTMIND_AGENT_JDBC_INTEGRATION_TEST", matches = "true")
@SpringBootTest(properties = {
        "agentmind.agent.persistence.store=jdbc",
        "agentmind.study.flashcard.algorithm=fsrs",
        "agentmind.vector-store.type=memory",
        "spring.datasource.url=${AGENTMIND_POSTGRES_JDBC_URL:jdbc:postgresql://localhost:5432/agentmind}",
        "spring.datasource.username=${AGENTMIND_POSTGRES_USERNAME:agentmind}",
        "spring.datasource.password=${AGENTMIND_POSTGRES_PASSWORD:agentmind_dev_password}"
})
@AutoConfigureMockMvc
class JdbcStudyPersonalizationIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StudyFlashcardRepository flashcardRepository;

    @Autowired
    private ChatMemoryRepository chatMemoryRepository;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpSchema() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            executePostgreSqlSchema(connection);
        }
        // 严格按外键依赖从明细到主表清理，测试只能连接本地开发数据库。
        jdbcTemplate.update("delete from daily_study_task_events");
        jdbcTemplate.update("delete from daily_study_task_cards");
        jdbcTemplate.update("delete from daily_study_tasks");
        jdbcTemplate.update("delete from daily_study_plans");
        jdbcTemplate.update("delete from conversation_learning_summaries");
        jdbcTemplate.update("delete from learning_topic_profiles");
        jdbcTemplate.update("delete from study_review_session_items");
        jdbcTemplate.update("delete from study_review_sessions");
        jdbcTemplate.update("delete from fsrs_user_profile_versions");
        jdbcTemplate.update("delete from study_flashcard_fsrs_states");
        jdbcTemplate.update("delete from fsrs_parameter_optimization_jobs");
        jdbcTemplate.update("delete from fsrs_user_profiles");
        jdbcTemplate.update("delete from study_flashcard_reviews");
        jdbcTemplate.update("delete from study_flashcards");
    }

    private void executePostgreSqlSchema(Connection connection) throws Exception {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("db/schema/agent_write_tools.sql")
        );
        // PostgreSQL 匿名块内部包含分号，整份交给数据库解析可避免测试工具错误拆分 DO $$ 块。
        populator.setSeparator(ScriptUtils.EOF_STATEMENT_SEPARATOR);
        populator.populate(connection);
    }

    @Test
    void personalizationWorkflowShouldPersistEveryStage() throws Exception {
        long workspaceId = 48_001L;
        saveCard(workspaceId);
        createWeakTopicConversation(workspaceId);

        MvcResult initialProfile = mockMvc.perform(get(
                            "/api/v1/workspaces/{workspaceId}/study/fsrs/profile", workspaceId
                        ))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode parameters = objectMapper.readTree(initialProfile.getResponse().getContentAsString())
                .path("data").path("parameters");
        var updateBody = objectMapper.createObjectNode();
        updateBody.set("parameters", parameters);
        updateBody.put("desiredRetention", 0.92);
        mockMvc.perform(put("/api/v1/workspaces/{workspaceId}/study/fsrs/profile", workspaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version", equalTo(1)));
        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/study/fsrs/profile/rollback", workspaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetVersion\":0,\"expectedCurrentVersion\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version", equalTo(2)))
                .andExpect(jsonPath("$.data.source", equalTo("ROLLBACK")));

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/study/learning-profile/refresh", workspaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].topic", equalTo("数据库事务")));
        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/study/conversation-summaries/refresh", workspaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].weakTopics[0]", equalTo("数据库事务")));

        LocalDate planDate = LocalDate.now().plusDays(1);
        MvcResult plan = mockMvc.perform(post(
                            "/api/v1/workspaces/{workspaceId}/study-plans/daily", workspaceId
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planDate\":\"" + planDate + "\",\"dailyReviewTarget\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tasks[?(@.type == 'MASTERY_REINFORCEMENT')]").isNotEmpty())
                .andExpect(jsonPath("$.data.tasks[?(@.type == 'CONVERSATION_REVIEW')]").isNotEmpty())
                .andReturn();
        long taskId = objectMapper.readTree(plan.getResponse().getContentAsString())
                .path("data").path("tasks").get(0).path("id").asLong();
        mockMvc.perform(post(
                            "/api/v1/workspaces/{workspaceId}/study-tasks/{taskId}/complete",
                            workspaceId, taskId
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0,\"comment\":\"集成测试完成\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", equalTo("COMPLETED")));
        mockMvc.perform(post(
                            "/api/v1/workspaces/{workspaceId}/study-tasks/{taskId}/feedback",
                            workspaceId, taskId
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":1,\"score\":5,\"comment\":\"内容有效\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version", equalTo(2)));

        assertThat(count("fsrs_user_profile_versions")).isEqualTo(3L);
        assertThat(count("learning_topic_profiles")).isEqualTo(1L);
        assertThat(count("conversation_learning_summaries")).isEqualTo(1L);
        assertThat(count("daily_study_task_events")).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject(
                "select feedback_score from daily_study_tasks where id = ?", Integer.class, taskId
        )).isEqualTo(5);
    }

    private void saveCard(long workspaceId) {
        OffsetDateTime now = OffsetDateTime.now();
        flashcardRepository.save(new StudyFlashcard(
                null, 1L, workspaceId, null, 9_001L, "数据库事务",
                "jdbc-profile-" + UUID.randomUUID(), "什么是事务隔离？", "隔离并发事务间的中间状态。", null,
                StudyFlashcardStatus.REVIEW, 3, 7, 2.5, 3,
                now.minusDays(1), now.minusDays(7), 0, now, now
        ));
    }

    private void createWeakTopicConversation(long workspaceId) {
        var conversation = chatMemoryRepository.createConversation(workspaceId, "数据库事务复习");
        chatMemoryRepository.createMessage(
                workspaceId, conversation.id(), ChatMessageRole.USER, ChatMessageStatus.COMPLETED,
                "我不懂数据库事务隔离，这个知识点很薄弱"
        );
        chatMemoryRepository.createMessage(
                workspaceId, conversation.id(), ChatMessageRole.ASSISTANT, ChatMessageStatus.COMPLETED,
                "建议结合异常现象复习四种隔离级别。"
        );
    }

    private long count(String tableName) {
        // 表名只来自测试代码中的固定常量，不能接受外部输入。
        Long count = jdbcTemplate.queryForObject("select count(*) from " + tableName, Long.class);
        return count == null ? 0 : count;
    }
}
