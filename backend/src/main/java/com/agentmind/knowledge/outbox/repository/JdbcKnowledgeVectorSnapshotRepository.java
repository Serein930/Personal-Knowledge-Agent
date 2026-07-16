package com.agentmind.knowledge.outbox.repository;

import com.agentmind.document.chunk.DocumentChunk;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 基于游标分页读取向量表，避免重建大知识空间时一次加载全部文档。 */
@Repository
@ConditionalOnProperty(prefix = "agentmind.knowledge-index.outbox", name = "enabled", havingValue = "true")
public class JdbcKnowledgeVectorSnapshotRepository implements KnowledgeVectorSnapshotRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcKnowledgeVectorSnapshotRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<Long> findDocumentIds(Long workspaceId, Long afterDocumentId, int limit) {
        return jdbcTemplate.queryForList("""
                select distinct document_id
                from knowledge_vector_chunks
                where workspace_id = ? and document_id > ?
                order by document_id
                limit ?
                """, Long.class, workspaceId, afterDocumentId, limit);
    }

    @Override
    public List<DocumentChunk> findChunks(Long workspaceId, Long documentId) {
        return jdbcTemplate.query("""
                select chunk_id, document_id, chunk_sequence, heading_path, content
                from knowledge_vector_chunks
                where workspace_id = ? and document_id = ?
                order by chunk_sequence
                """, (resultSet, rowNumber) -> {
            String content = resultSet.getString("content");
            return new DocumentChunk(
                    resultSet.getString("chunk_id"),
                    resultSet.getLong("document_id"),
                    resultSet.getInt("chunk_sequence"),
                    resultSet.getString("heading_path"),
                    content,
                    0,
                    content.length()
            );
        }, workspaceId, documentId);
    }
}
