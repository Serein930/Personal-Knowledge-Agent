package com.agentmind.study.flashcard.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.study.flashcard.model.StudyFlashcard;
import com.agentmind.study.flashcard.model.StudyFlashcardStatus;
import com.agentmind.study.flashcard.model.dto.SubmitFlashcardReviewRequest;
import com.agentmind.study.flashcard.model.dto.SubmittedFlashcardReviewResponse;
import com.agentmind.study.flashcard.repository.StudyFlashcardRepository;
import com.agentmind.study.flashcard.repository.StudyFlashcardReviewRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 复习评分并发与重复提交测试。
 */
@SpringBootTest
class StudyFlashcardReviewConcurrencyTests {

    @Autowired
    private StudyFlashcardReviewApplicationService reviewService;

    @Autowired
    private StudyFlashcardRepository flashcardRepository;

    @Autowired
    private StudyFlashcardReviewRepository reviewRepository;

    @Test
    void concurrentDuplicateRequestShouldAdvanceScheduleOnlyOnce() throws Exception {
        long workspaceId = 44_101L;
        StudyFlashcard flashcard = saveCard(workspaceId);
        AgentToolExecutionContext context = new AgentToolExecutionContext(1L, workspaceId, null);
        String requestId = "concurrent-duplicate-" + UUID.randomUUID();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<SubmittedFlashcardReviewResponse>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < 16; index++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return reviewService.submit(
                            context,
                            flashcard.id(),
                            new SubmitFlashcardReviewRequest(requestId, 5)
                    );
                }));
            }
            start.countDown();
            int newlyCreatedCount = 0;
            for (Future<SubmittedFlashcardReviewResponse> future : futures) {
                if (!future.get(10, TimeUnit.SECONDS).reused()) {
                    newlyCreatedCount++;
                }
            }
            assertThat(newlyCreatedCount).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }

        StudyFlashcard updated = flashcardRepository.findByOwnerUserIdAndWorkspaceIdAndId(
                1L, workspaceId, flashcard.id()
        ).orElseThrow();
        assertThat(updated.version()).isEqualTo(1);
        assertThat(updated.repetitionCount()).isEqualTo(1);
        assertThat(reviewRepository.countByOwnerUserIdAndWorkspaceIdAndFlashcardId(
                1L, workspaceId, flashcard.id()
        )).isEqualTo(1);
    }

    private StudyFlashcard saveCard(long workspaceId) {
        OffsetDateTime now = OffsetDateTime.now();
        return flashcardRepository.save(new StudyFlashcard(
                null, 1L, workspaceId, null, "concurrency-card-" + UUID.randomUUID(),
                "并发评分如何处理？", "使用请求幂等和版本条件更新。", null,
                StudyFlashcardStatus.NEW, 0, 0, 2.5, 0, now, null, 0, now, now
        ));
    }
}
