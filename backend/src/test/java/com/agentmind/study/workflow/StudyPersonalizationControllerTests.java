package com.agentmind.study.workflow;

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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Stage 8 个性化画像、长期摘要和任务状态机接口测试。 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class StudyPersonalizationControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StudyFlashcardRepository flashcardRepository;

    @Autowired
    private ChatMemoryRepository chatMemoryRepository;

    @Test
    void fsrsProfileShouldKeepVersionsAndRollbackAsANewVersion() throws Exception {
        // 演示鉴权仅开放用户 1；通过独立知识空间隔离本测试数据。
        long ownerUserId = 1L;
        long workspaceId = 47_001L;
        MvcResult initial = mockMvc.perform(get(
                            "/api/v1/workspaces/{workspaceId}/study/fsrs/profile", workspaceId
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version", equalTo(0)))
                .andReturn();
        JsonNode parameters = objectMapper.readTree(initial.getResponse().getContentAsString())
                .path("data").path("parameters");
        var updateNode = objectMapper.createObjectNode();
        updateNode.set("parameters", parameters);
        updateNode.put("desiredRetention", 0.91);
        String updateBody = updateNode.toString();

        mockMvc.perform(put("/api/v1/workspaces/{workspaceId}/study/fsrs/profile", workspaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version", equalTo(1)))
                .andExpect(jsonPath("$.data.source", equalTo("MANUAL")));
        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/study/fsrs/profile/versions", workspaceId)
                        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total", equalTo(2)))
                .andExpect(jsonPath("$.data.records[0].version", equalTo(1)));
        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/study/fsrs/profile/rollback", workspaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetVersion\":0,\"expectedCurrentVersion\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version", equalTo(2)))
                .andExpect(jsonPath("$.data.source", equalTo("ROLLBACK")));
    }

    @Test
    void planShouldUseProfileAndConversationAndSupportTaskWorkflow() throws Exception {
        long ownerUserId = 1L;
        long workspaceId = 47_002L;
        saveCard(ownerUserId, workspaceId, "并发编程", 3);
        saveCard(ownerUserId, workspaceId, "并发编程", 2);
        var conversation = chatMemoryRepository.createConversation(workspaceId, "并发编程学习");
        chatMemoryRepository.createMessage(
                workspaceId, conversation.id(), ChatMessageRole.USER, ChatMessageStatus.COMPLETED,
                "我不懂并发编程中的可见性，这部分很薄弱"
        );
        chatMemoryRepository.createMessage(
                workspaceId, conversation.id(), ChatMessageRole.ASSISTANT, ChatMessageStatus.COMPLETED,
                "可以从 happens-before 规则开始复习。"
        );

        LocalDate planDate = LocalDate.now().plusDays(1);
        MvcResult planResult = mockMvc.perform(post(
                            "/api/v1/workspaces/{workspaceId}/study-plans/daily", workspaceId
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planDate\":\"" + planDate + "\",\"dailyReviewTarget\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tasks[?(@.type == 'MASTERY_REINFORCEMENT')]").isNotEmpty())
                .andExpect(jsonPath("$.data.tasks[?(@.type == 'CONVERSATION_REVIEW')]").isNotEmpty())
                .andReturn();
        JsonNode tasks = objectMapper.readTree(planResult.getResponse().getContentAsString())
                .path("data").path("tasks");
        long completeTaskId = tasks.get(0).path("id").asLong();
        long rescheduleTaskId = tasks.get(1).path("id").asLong();

        mockMvc.perform(post(
                            "/api/v1/workspaces/{workspaceId}/study-tasks/{taskId}/complete",
                            workspaceId, completeTaskId
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0,\"comment\":\"已线下完成\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", equalTo("COMPLETED")))
                .andExpect(jsonPath("$.data.version", equalTo(1)));
        mockMvc.perform(post(
                            "/api/v1/workspaces/{workspaceId}/study-tasks/{taskId}/feedback",
                            workspaceId, completeTaskId
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":1,\"score\":4,\"comment\":\"难度合适\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feedbackScore", equalTo(4)))
                .andExpect(jsonPath("$.data.version", equalTo(2)));
        LocalDate newDate = planDate.plusDays(2);
        mockMvc.perform(post(
                            "/api/v1/workspaces/{workspaceId}/study-tasks/{taskId}/reschedule",
                            workspaceId, rescheduleTaskId
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0,\"targetDate\":\"" + newDate + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scheduledDate", equalTo(newDate.toString())))
                .andExpect(jsonPath("$.data.status", equalTo("PENDING")));
        mockMvc.perform(get(
                            "/api/v1/workspaces/{workspaceId}/study-tasks/{taskId}/events",
                            workspaceId, completeTaskId
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", equalTo(2)));

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/study/learning-profile", workspaceId)
                        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].topic", equalTo("并发编程")))
                .andExpect(jsonPath("$.data[0].level", equalTo("WEAK")));
        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/study/conversation-summaries", workspaceId)
                        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].weakTopics[0]", equalTo("并发编程")));
        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/study/maintenance/run", workspaceId)
                        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.running", equalTo(false)))
                .andExpect(jsonPath("$.data.processedScopes", equalTo(1)));
    }

    private StudyFlashcard saveCard(Long ownerUserId, Long workspaceId, String topic, int lapseCount) {
        OffsetDateTime now = OffsetDateTime.now();
        return flashcardRepository.save(new StudyFlashcard(
                null, ownerUserId, workspaceId, null, 8_001L, topic,
                "profile-" + UUID.randomUUID(), topic + "问题", topic + "答案", null,
                StudyFlashcardStatus.REVIEW, 3, 7, 2.5, lapseCount,
                now.minusDays(1), now.minusDays(7), 0, now, now
        ));
    }
}
