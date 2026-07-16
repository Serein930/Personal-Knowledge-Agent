package com.agentmind.knowledge.outbox.repository;

import com.agentmind.document.chunk.DocumentChunk;
import java.util.List;

/** 从 pgvector 主数据读取可重建的文档片段快照。 */
public interface KnowledgeVectorSnapshotRepository {

    List<Long> findDocumentIds(Long workspaceId, Long afterDocumentId, int limit);

    List<DocumentChunk> findChunks(Long workspaceId, Long documentId);
}
