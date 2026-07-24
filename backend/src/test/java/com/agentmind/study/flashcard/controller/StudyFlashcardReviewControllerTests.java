package com.agentmind.study.flashcard.controller;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agentmind.study.flashcard.model.StudyFlashcard;
import com.agentmind.study.flashcard.model.StudyFlashcardStatus;
import com.agentmind.study.flashcard.repository.StudyFlashcardRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 复习卡片到期查询、评分幂等和知识空间隔离接口测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
class StudyFlashcardReviewControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudyFlashcardRepository flashcardRepository;

    @Test
    void dueCardShouldLeaveDueListAfterIdempotentSuccessfulReview() throws Exception {
        long workspaceId = 44_001L;
        StudyFlashcard flashcard = saveCard(workspaceId, OffsetDateTime.now().minusMinutes(1));
        String requestId = "review-idempotent-" + UUID.randomUUID();

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/flashcards/due", workspaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total", equalTo(1)))
                .andExpect(jsonPath("$.data.records[0].id", equalTo(flashcard.id().intValue())));

        String requestBody = """
                {"requestId":"%s","score":5}
                """.formatted(requestId);
        mockMvc.perform(post(
                            "/api/v1/workspaces/{workspaceId}/flashcards/{flashcardId}/reviews",
                            workspaceId,
                            flashcard.id()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reused", equalTo(false)))
                .andExpect(jsonPath("$.data.flashcard.status", equalTo("LEARNING")))
                .andExpect(jsonPath("$.data.flashcard.intervalDays", equalTo(1)))
                .andExpect(jsonPath("$.data.flashcard.version", equalTo(1)))
                .andExpect(jsonPath("$.data.review.algorithm", equalTo("sm2-v1")));

        mockMvc.perform(post(
                            "/api/v1/workspaces/{workspaceId}/flashcards/{flashcardId}/reviews",
                            workspaceId,
                            flashcard.id()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reused", equalTo(true)))
                .andExpect(jsonPath("$.data.flashcard.version", equalTo(1)));

        mockMvc.perform(get(
                            "/api/v1/workspaces/{workspaceId}/flashcards/{flashcardId}/reviews",
                            workspaceId,
                            flashcard.id()
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total", equalTo(1)));

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/flashcards/due", workspaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total", equalTo(0)));
    }

    @Test
    void repeatedRequestWithDifferentScoreShouldConflict() throws Exception {
        long workspaceId = 44_002L;
        StudyFlashcard flashcard = saveCard(workspaceId, OffsetDateTime.now());
        String requestId = "review-conflict-" + UUID.randomUUID();

        submit(workspaceId, flashcard.id(), requestId, 4).andExpect(status().isOk());
        submit(workspaceId, flashcard.id(), requestId, 2)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("RESOURCE_CONFLICT")));
    }

    @Test
    void workspaceBoundaryAndScoreValidationShouldBeEnforced() throws Exception {
        long workspaceId = 44_003L;
        StudyFlashcard flashcard = saveCard(workspaceId, OffsetDateTime.now());

        submit(44_004L, flashcard.id(), "wrong-workspace", 5)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("RESOURCE_NOT_FOUND")));

        submit(workspaceId, flashcard.id(), "invalid-score", 6)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("BAD_REQUEST")));
    }

    @Test
    void bulkDeleteShouldSupportSelectedCardsAndClearingWorkspace() throws Exception {
        long workspaceId = 44_005L;
        StudyFlashcard selectedCard = saveCard(workspaceId, OffsetDateTime.now());
        StudyFlashcard retainedCard = saveCard(workspaceId, OffsetDateTime.now());

        mockMvc.perform(post(
                            "/api/v1/workspaces/{workspaceId}/flashcards/bulk-delete",
                            workspaceId
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cardIds":[%d],"deleteAll":false}
                                """.formatted(selectedCard.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deletedCount", equalTo(1)));

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/flashcards", workspaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total", equalTo(1)))
                .andExpect(jsonPath("$.data.records[0].id", equalTo(retainedCard.id().intValue())));

        mockMvc.perform(post(
                            "/api/v1/workspaces/{workspaceId}/flashcards/bulk-delete",
                            workspaceId
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cardIds":[],"deleteAll":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deletedCount", equalTo(1)));

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/flashcards", workspaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total", equalTo(0)));
    }

    private org.springframework.test.web.servlet.ResultActions submit(
            long workspaceId,
            long flashcardId,
            String requestId,
            int score
    ) throws Exception {
        return mockMvc.perform(post(
                            "/api/v1/workspaces/{workspaceId}/flashcards/{flashcardId}/reviews",
                            workspaceId,
                            flashcardId
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestId":"%s","score":%d}
                                """.formatted(requestId, score)));
    }

    private StudyFlashcard saveCard(long workspaceId, OffsetDateTime dueAt) {
        OffsetDateTime now = OffsetDateTime.now();
        return flashcardRepository.save(new StudyFlashcard(
                null,
                1L,
                workspaceId,
                null,
                "test-card-" + UUID.randomUUID(),
                "什么是间隔重复？",
                "根据记忆强度动态安排下一次复习。",
                null,
                StudyFlashcardStatus.NEW,
                0,
                0,
                2.5,
                0,
                dueAt,
                null,
                0,
                now,
                now
        ));
    }
}
