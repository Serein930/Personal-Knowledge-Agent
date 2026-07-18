package com.agentmind.document.repository;

import com.agentmind.document.chunk.DocumentChunk;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL 文档片段仓储，通过替换写入保证同一文档不会残留旧解析结果。 */
@Repository
@ConditionalOnProperty(prefix = "agentmind.core.persistence", name = "store", havingValue = "jdbc")
public class JdbcDocumentChunkRepository implements DocumentChunkRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcDocumentChunkRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void replaceDocumentChunks(Long ownerUserId, Long workspaceId, Long documentId,
            List<DocumentChunk> chunks) {
        deleteAllByDocumentId(documentId);
        for (DocumentChunk chunk : chunks) {
            jdbcTemplate.update("""
                    insert into document_chunk (
                        id, owner_user_id, workspace_id, document_id, chunk_sequence,
                        heading_path, content, char_start, char_end
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, chunk.id(), ownerUserId, workspaceId, documentId, chunk.sequence(),
                    chunk.headingPath(), chunk.content(), chunk.charStart(), chunk.charEnd());
        }
    }

    @Override
    public List<DocumentChunk> findAllByDocumentId(Long documentId) {
        return jdbcTemplate.query("""
                select id, document_id, chunk_sequence, heading_path, content, char_start, char_end
                from document_chunk where document_id = ? order by chunk_sequence
                """, (resultSet, rowNumber) -> new DocumentChunk(resultSet.getString("id"),
                resultSet.getLong("document_id"), resultSet.getInt("chunk_sequence"),
                resultSet.getString("heading_path"), resultSet.getString("content"),
                resultSet.getInt("char_start"), resultSet.getInt("char_end")), documentId);
    }

    @Override
    public void deleteAllByDocumentId(Long documentId) {
        jdbcTemplate.update("delete from document_chunk where document_id = ?", documentId);
    }
}
