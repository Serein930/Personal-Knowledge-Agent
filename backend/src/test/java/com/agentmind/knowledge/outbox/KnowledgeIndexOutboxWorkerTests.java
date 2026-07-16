package com.agentmind.knowledge.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentmind.document.chunk.DocumentChunk;
import com.agentmind.knowledge.keyword.KeywordIndex;
import com.agentmind.knowledge.outbox.config.KnowledgeIndexOutboxProperties;
import com.agentmind.knowledge.outbox.model.KnowledgeIndexOutboxEvent;
import com.agentmind.knowledge.outbox.model.KnowledgeIndexOutboxOperation;
import com.agentmind.knowledge.outbox.model.KnowledgeIndexOutboxPayload;
import com.agentmind.knowledge.outbox.model.KnowledgeIndexOutboxStatistics;
import com.agentmind.knowledge.outbox.model.KnowledgeIndexOutboxStatus;
import com.agentmind.knowledge.outbox.repository.KnowledgeIndexOutboxRepository;
import com.agentmind.knowledge.outbox.service.KnowledgeIndexOutboxWorker;
import com.agentmind.knowledge.model.dto.KnowledgeSearchResultResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 验证外部索引持续失败时，消息会按上限进入死信而不会静默丢失。 */
class KnowledgeIndexOutboxWorkerTests {

    @Test
    void shouldMoveMessageToDeadStatusAfterMaximumAttempts() {
        RecordingRepository repository = new RecordingRepository();
        KnowledgeIndexOutboxProperties properties = new KnowledgeIndexOutboxProperties();
        properties.setInstanceId("fault-test");
        properties.setMaximumAttempts(1);
        KnowledgeIndexOutboxWorker worker = new KnowledgeIndexOutboxWorker(
                repository, new AlwaysFailingKeywordIndex(), properties, new SimpleMeterRegistry());

        assertThat(worker.processOnce()).isEqualTo(1);
        assertThat(repository.dead).isTrue();
        assertThat(repository.failureReason).contains("模拟 OpenSearch 故障");
    }

    private static final class RecordingRepository implements KnowledgeIndexOutboxRepository {
        private boolean dead;
        private String failureReason;

        @Override
        public void enqueue(String eventKey, Long workspaceId, Long documentId,
                KnowledgeIndexOutboxOperation operation, KnowledgeIndexOutboxPayload payload, OffsetDateTime now) {
        }

        @Override
        public List<KnowledgeIndexOutboxEvent> claimBatch(
                Long workspaceId, String leaseOwner, OffsetDateTime now, OffsetDateTime leaseExpiresAt, int limit) {
            DocumentChunk chunk = new DocumentChunk("1-0", 1L, 0, "故障", "测试", 0, 2);
            return List.of(new KnowledgeIndexOutboxEvent(1L, "fault", 1L, 1L,
                    KnowledgeIndexOutboxOperation.UPSERT, new KnowledgeIndexOutboxPayload(List.of(chunk)),
                    KnowledgeIndexOutboxStatus.PROCESSING, 1, now, leaseOwner, leaseExpiresAt,
                    "", now, now, null));
        }

        @Override
        public boolean markCompleted(Long eventId, String leaseOwner, OffsetDateTime now) {
            return false;
        }

        @Override
        public boolean markFailed(Long eventId, String leaseOwner, String reason,
                OffsetDateTime nextAvailableAt, boolean dead, OffsetDateTime now) {
            this.dead = dead;
            this.failureReason = reason;
            return true;
        }

        @Override
        public KnowledgeIndexOutboxStatistics statistics(Long workspaceId) {
            return new KnowledgeIndexOutboxStatistics(0, 0, 0, 0, dead ? 1 : 0);
        }
    }

    private static final class AlwaysFailingKeywordIndex implements KeywordIndex {
        @Override
        public void replaceDocumentChunks(Long workspaceId, Long documentId, List<DocumentChunk> chunks) {
            throw new IllegalStateException("模拟 OpenSearch 故障");
        }

        @Override
        public void deleteDocumentChunks(Long workspaceId, Long documentId) {
            throw new IllegalStateException("模拟 OpenSearch 故障");
        }

        @Override
        public List<KnowledgeSearchResultResponse> search(Long workspaceId, String query, int topK) {
            return List.of();
        }
    }
}
