package com.agentmind.knowledge.model;

import java.time.OffsetDateTime;

/**
 * Vector record generated from one document chunk.
 *
 * <p>This model deliberately carries workspaceId and documentId because all future retrieval must be scoped by
 * user workspace. When pgvector is introduced, these fields should become queryable columns beside the vector.</p>
 */
public record KnowledgeVector(
        String id,
        Long workspaceId,
        Long documentId,
        String chunkId,
        int chunkSequence,
        String headingPath,
        String content,
        float[] embedding,
        OffsetDateTime createdAt
) {
}
