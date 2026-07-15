package com.agentmind.study.workflow;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Stage 8 复习管理、会话、统计和每日计划完整接口测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
class StudyWorkflowControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StudyFlashcardRepository flashcardRepository;

    @Test
    void cardManagementShouldUseVersionAndWorkspaceBoundary() throws Exception {
        long workspaceId = 46_001L;
        StudyFlashcard card = saveCard(workspaceId, StudyFlashcardStatus.NEW, 0, OffsetDateTime.now().minusMinutes(1));

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/flashcards/{cardId}/suspend", workspaceId, card.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", equalTo("SUSPENDED")))
                .andExpect(jsonPath("$.data.version", equalTo(1)));

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/flashcards/due", workspaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total", equalTo(0)));

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/flashcards/{cardId}/resume", workspaceId, card.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", equalTo("NEW")))
                .andExpect(jsonPath("$.data.version", equalTo(2)));

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/flashcards/{cardId}/reschedule", workspaceId, card.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":2,"dueAt":"%s"}
                                """.formatted(OffsetDateTime.now().plusDays(2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version", equalTo(3)));

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/flashcards/{cardId}/suspend", workspaceId, card.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":2}"))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/flashcards/{cardId}/suspend", 46_999L, card.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":3}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void reviewSessionShouldDriveStatisticsAndDailyPlan() throws Exception {
        long workspaceId = 46_002L;
        saveCard(workspaceId, StudyFlashcardStatus.NEW, 0, OffsetDateTime.now().minusMinutes(2));
        saveCard(workspaceId, StudyFlashcardStatus.NEW, 0, OffsetDateTime.now().minusMinutes(1));
        saveCard(workspaceId, StudyFlashcardStatus.REVIEW, 30, OffsetDateTime.now().plusDays(30));

        String planRequest = """
                {"planDate":"%s","dailyReviewTarget":2}
                """.formatted(LocalDate.now());
        MvcResult savedPlan = mockMvc.perform(post(
                            "/api/v1/workspaces/{workspaceId}/study-plans/daily", workspaceId
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dailyReviewTarget", equalTo(2)))
                .andReturn();
        long planId = objectMapper.readTree(savedPlan.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        MvcResult created = mockMvc.perform(post(
                            "/api/v1/workspaces/{workspaceId}/review-sessions", workspaceId
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"limit\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCards", equalTo(2)))
                .andReturn();
        JsonNode session = objectMapper.readTree(created.getResponse().getContentAsString()).path("data");
        long sessionId = session.path("id").asLong();
        long firstCardId = session.path("queue").get(0).path("flashcard").path("id").asLong();
        long secondCardId = session.path("queue").get(1).path("flashcard").path("id").asLong();

        submitSessionReview(workspaceId, sessionId, firstCardId, "workflow-good", 5)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.session.reviewedCards", equalTo(1)))
                .andExpect(jsonPath("$.data.session.status", equalTo("IN_PROGRESS")));
        submitSessionReview(workspaceId, sessionId, secondCardId, "workflow-failed", 2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.session.reviewedCards", equalTo(2)))
                .andExpect(jsonPath("$.data.session.correctCards", equalTo(1)))
                .andExpect(jsonPath("$.data.session.status", equalTo("COMPLETED")));

        // 会话完成后网络重试仍复用原评分，不能重复增加会话进度。
        submitSessionReview(workspaceId, sessionId, firstCardId, "workflow-good", 5)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.review.reused", equalTo(true)))
                .andExpect(jsonPath("$.data.session.reviewedCards", equalTo(2)));

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/flashcards/statistics", workspaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completedToday", equalTo(2)))
                .andExpect(jsonPath("$.data.accuracyToday", closeTo(50.0, 0.01)))
                .andExpect(jsonPath("$.data.lapseRate", closeTo(50.0, 0.01)))
                .andExpect(jsonPath("$.data.currentStreakDays", equalTo(1)))
                .andExpect(jsonPath("$.data.maturity.matureCount", equalTo(1)));

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/study-plans/daily", workspaceId)
                        .param("date", LocalDate.now().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", equalTo((int) planId)))
                .andExpect(jsonPath("$.data.completed", equalTo(true)))
                .andExpect(jsonPath("$.data.progress", closeTo(100.0, 0.01)));

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/study-plans/daily", workspaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"planDate":"%s","dailyReviewTarget":4}
                                """.formatted(LocalDate.now())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", equalTo((int) planId)))
                .andExpect(jsonPath("$.data.dailyReviewTarget", equalTo(4)));

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/review-sessions/{sessionId}", 46_999L, sessionId))
                .andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.ResultActions submitSessionReview(
            long workspaceId,
            long sessionId,
            long cardId,
            String requestId,
            int score
    ) throws Exception {
        return mockMvc.perform(post(
                            "/api/v1/workspaces/{workspaceId}/review-sessions/{sessionId}/cards/{cardId}/reviews",
                            workspaceId,
                            sessionId,
                            cardId
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestId":"%s","score":%d}
                                """.formatted(requestId, score)));
    }

    private StudyFlashcard saveCard(
            long workspaceId,
            StudyFlashcardStatus status,
            int intervalDays,
            OffsetDateTime dueAt
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        return flashcardRepository.save(new StudyFlashcard(
                null, 1L, workspaceId, null, "workflow-card-" + UUID.randomUUID(),
                "复习工作流测试问题", "复习工作流测试答案", null,
                status, status == StudyFlashcardStatus.REVIEW ? 3 : 0, intervalDays, 2.5, 0,
                dueAt, status == StudyFlashcardStatus.REVIEW ? now.minusDays(intervalDays) : null,
                0, now, now
        ));
    }
}
