package com.agentmind.study.flashcard.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.study.flashcard.model.StudyFlashcard;
import com.agentmind.study.flashcard.model.StudyFlashcardStatus;
import com.agentmind.study.flashcard.model.dto.SubmitFlashcardReviewRequest;
import com.agentmind.study.flashcard.model.dto.SubmittedFlashcardReviewResponse;
import com.agentmind.study.flashcard.service.StudyFlashcardReviewApplicationService;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 复习卡片 PostgreSQL 并发与幂等手动集成测试。
 *
 * <p>只有设置 {@code AGENTMIND_AGENT_JDBC_INTEGRATION_TEST=true} 时才会运行。测试会初始化
 * Stage 7 与 Stage 8 共用的数据表并清空复习卡片数据，因此只能连接本地开发数据库。</p>
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
class JdbcStudyFlashcardReviewIntegrationTests {

    @Autowired
    private StudyFlashcardReviewApplicationService reviewService;

    @Autowired
    private StudyFlashcardRepository flashcardRepository;

    @Autowired
    private StudyFlashcardReviewRepository reviewRepository;

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpSchema() {
        // 复习模块和 Agent 写工具共享迁移，测试不得再单独执行历史建表脚本。
        flyway.migrate();
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
        jdbcTemplate.update("delete from study_flashcard_reviews");
        jdbcTemplate.update("delete from study_flashcards");
    }

    @Test
    void concurrentDifferentRequestsShouldNotLoseScheduleUpdate() throws Exception {
        long workspaceId = 45_001L;
        StudyFlashcard flashcard = saveCard(workspaceId);
        AgentToolExecutionContext context = new AgentToolExecutionContext(1L, workspaceId, null);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<SubmittedFlashcardReviewResponse> first = executor.submit(() -> {
                start.await();
                return reviewService.submit(
                        context,
                        flashcard.id(),
                        new SubmitFlashcardReviewRequest("jdbc-review-first", 5)
                );
            });
            Future<SubmittedFlashcardReviewResponse> second = executor.submit(() -> {
                start.await();
                return reviewService.submit(
                        context,
                        flashcard.id(),
                        new SubmitFlashcardReviewRequest("jdbc-review-second", 4)
                );
            });
            start.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS).reused()).isFalse();
            assertThat(second.get(10, TimeUnit.SECONDS).reused()).isFalse();
        } finally {
            executor.shutdownNow();
        }

        StudyFlashcard updated = requireCard(workspaceId, flashcard.id());
        assertThat(updated.version()).isEqualTo(2);
        assertThat(updated.repetitionCount()).isEqualTo(2);
        assertThat(reviewRepository.countByOwnerUserIdAndWorkspaceIdAndFlashcardId(
                1L, workspaceId, flashcard.id()
        )).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from study_flashcard_fsrs_states where flashcard_id = ?",
                Long.class, flashcard.id()
        )).isEqualTo(1L);
    }

    @Test
    void concurrentDuplicateRequestShouldPersistOnlyOneReview() throws Exception {
        long workspaceId = 45_002L;
        StudyFlashcard flashcard = saveCard(workspaceId);
        AgentToolExecutionContext context = new AgentToolExecutionContext(1L, workspaceId, null);
        String requestId = "jdbc-duplicate-" + UUID.randomUUID();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<SubmittedFlashcardReviewResponse>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < 12; index++) {
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

        StudyFlashcard updated = requireCard(workspaceId, flashcard.id());
        assertThat(updated.version()).isEqualTo(1);
        assertThat(reviewRepository.countByOwnerUserIdAndWorkspaceIdAndFlashcardId(
                1L, workspaceId, flashcard.id()
        )).isEqualTo(1);
    }

    private StudyFlashcard saveCard(long workspaceId) {
        OffsetDateTime now = OffsetDateTime.now();
        return flashcardRepository.save(new StudyFlashcard(
                null,
                1L,
                workspaceId,
                null,
                "jdbc-review-card-" + UUID.randomUUID(),
                "如何防止并发复习丢失更新？",
                "使用版本条件更新，并在冲突后重新读取和计算。",
                null,
                StudyFlashcardStatus.NEW,
                0,
                0,
                2.5,
                0,
                now,
                null,
                0,
                now,
                now
        ));
    }

    private StudyFlashcard requireCard(long workspaceId, long flashcardId) {
        return flashcardRepository.findByOwnerUserIdAndWorkspaceIdAndId(
                1L, workspaceId, flashcardId
        ).orElseThrow();
    }
}
