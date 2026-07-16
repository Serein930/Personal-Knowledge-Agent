package com.agentmind.document.repository;

import com.agentmind.document.model.DocumentMetadata;
import com.agentmind.document.model.DocumentSourceType;
import com.agentmind.document.model.IngestionStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/** 测试和零依赖开发模式使用的文档元数据仓储。 */
@Repository
@ConditionalOnProperty(prefix = "agentmind.core.persistence", name = "store", havingValue = "memory", matchIfMissing = true)
public class InMemoryDocumentMetadataRepository implements DocumentMetadataRepository {

    private final AtomicLong idGenerator = new AtomicLong(100);
    private final Map<Long, DocumentMetadata> documents = new ConcurrentHashMap<>();

    public InMemoryDocumentMetadataRepository() {
        OffsetDateTime now = OffsetDateTime.now();
        documents.put(1L, new DocumentMetadata(1L, 1L, 1L, "Java concurrency notes",
                DocumentSourceType.MARKDOWN, null, "concurrency.md", "", "text/markdown", 0, "",
                List.of("Java", "Concurrency", "Thread Pool"), IngestionStatus.SUCCEEDED, 2,
                now.minusDays(1), now.minusDays(1)));
        documents.put(2L, new DocumentMetadata(2L, 1L, 1L, "Spring AI reference excerpt",
                DocumentSourceType.WEB_PAGE, "https://docs.spring.io/spring-ai/reference/", null,
                "", "text/html", 0, "", List.of("Spring AI", "RAG", "Tool Calling"),
                IngestionStatus.RUNNING, 0, now, now));
    }

    @Override
    public synchronized DocumentMetadata create(Long ownerUserId, Long workspaceId, String title,
            DocumentSourceType sourceType, String sourceUri, String originalFilename, List<String> tags) {
        long id = idGenerator.incrementAndGet();
        OffsetDateTime now = OffsetDateTime.now();
        DocumentMetadata metadata = new DocumentMetadata(id, ownerUserId, workspaceId, title, sourceType,
                sourceUri, originalFilename, "", "", 0, "", tags, IngestionStatus.RUNNING, 0, now, now);
        documents.put(id, metadata);
        return metadata;
    }

    @Override
    public void markSucceeded(Long documentId, String storageKey, String contentType, long contentSize, int chunkCount) {
        documents.computeIfPresent(documentId, (id, current) -> copy(current, storageKey, contentType,
                contentSize, IngestionStatus.SUCCEEDED, chunkCount));
    }

    @Override
    public void markFailed(Long documentId) {
        documents.computeIfPresent(documentId, (id, current) -> copy(current, current.storageKey(),
                current.contentType(), current.contentSize(), IngestionStatus.FAILED, 0));
    }

    @Override
    public Optional<DocumentMetadata> findByWorkspaceIdAndId(Long workspaceId, Long documentId) {
        return Optional.ofNullable(documents.get(documentId))
                .filter(document -> workspaceId.equals(document.workspaceId()));
    }

    @Override
    public Optional<DocumentMetadata> findById(Long documentId) {
        return Optional.ofNullable(documents.get(documentId));
    }

    @Override
    public List<DocumentMetadata> findAllByWorkspaceId(Long workspaceId) {
        return documents.values().stream().filter(document -> workspaceId.equals(document.workspaceId())).toList();
    }

    private DocumentMetadata copy(DocumentMetadata current, String storageKey, String contentType,
            long contentSize, IngestionStatus status, int chunkCount) {
        return new DocumentMetadata(current.id(), current.ownerUserId(), current.workspaceId(), current.title(),
                current.sourceType(), current.sourceUri(), current.originalFilename(), storageKey, contentType,
                contentSize, current.contentHash(), current.tags(), status, chunkCount,
                current.createdAt(), OffsetDateTime.now());
    }
}
