package com.agentmind.knowledge.repository;

import com.agentmind.knowledge.model.KnowledgeVector;
import com.agentmind.knowledge.model.VectorSearchResult;
import com.agentmind.knowledge.vector.VectorStore;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * 基于关系数据库与数据库向量扩展的向量库实现。
 *
 * <p>该适配器使用原生数据库访问方式，让项目默认内存模式保持轻量。
 * 只有当向量库类型切换为数据库向量扩展且存在数据源时才启用。
 * 后续基础设施阶段可以继续加入数据库访问模板、数据库迁移和连接池配置，而不改变面向应用层的向量库端口。</p>
 */
@Repository
@ConditionalOnProperty(prefix = "agentmind.vector-store", name = "type", havingValue = "pgvector")
public class PgVectorStore implements VectorStore {

    private static final String DELETE_DOCUMENT_SQL = """
            delete from knowledge_vector_chunks
            where workspace_id = ?
              and document_id = ?
            """;

    private static final String INSERT_VECTOR_SQL = """
            insert into knowledge_vector_chunks (
                id, workspace_id, document_id, chunk_id, chunk_sequence,
                heading_path, content, embedding, created_at
            ) values (?, ?, ?, ?, ?, ?, ?, cast(? as vector), ?)
            """;

    private static final String SEARCH_SQL = """
            select
                chunk_id,
                document_id,
                chunk_sequence,
                heading_path,
                content,
                1 - (embedding <=> cast(? as vector)) as score
            from knowledge_vector_chunks
            where workspace_id = ?
            order by embedding <=> cast(? as vector)
            limit ?
            """;

    private final DataSource dataSource;
    private final int embeddingDimensions;

    public PgVectorStore(
            DataSource dataSource,
            @Value("${agentmind.vector-store.embedding-dimensions:128}") int embeddingDimensions
    ) {
        this.dataSource = dataSource;
        this.embeddingDimensions = embeddingDimensions;
    }

    @Override
    public void replaceDocumentVectors(Long workspaceId, Long documentId, Collection<KnowledgeVector> vectors) {
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                deleteDocumentVectors(connection, workspaceId, documentId);
                insertVectors(connection, vectors);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("替换 pgvector 文档向量失败", exception);
        }
    }

    @Override
    public void deleteDocumentVectors(Long workspaceId, Long documentId) {
        try (Connection connection = dataSource.getConnection()) {
            deleteDocumentVectors(connection, workspaceId, documentId);
        } catch (SQLException exception) {
            throw new IllegalStateException("删除 pgvector 文档向量失败", exception);
        }
    }

    @Override
    public List<VectorSearchResult> search(Long workspaceId, float[] queryEmbedding, int topK) {
        validateEmbeddingDimensions(queryEmbedding);
        String queryVector = PgVectorSqlSupport.toVectorLiteral(queryEmbedding);
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(SEARCH_SQL)) {
            statement.setString(1, queryVector);
            statement.setLong(2, workspaceId);
            statement.setString(3, queryVector);
            statement.setInt(4, topK);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<VectorSearchResult> results = new ArrayList<>();
                while (resultSet.next()) {
                    results.add(mapSearchResult(resultSet));
                }
                return results;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("检索 pgvector 文档片段失败", exception);
        }
    }

    private void deleteDocumentVectors(Connection connection, Long workspaceId, Long documentId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(DELETE_DOCUMENT_SQL)) {
            statement.setLong(1, workspaceId);
            statement.setLong(2, documentId);
            statement.executeUpdate();
        }
    }

    private void insertVectors(Connection connection, Collection<KnowledgeVector> vectors) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_VECTOR_SQL)) {
            for (KnowledgeVector vector : vectors) {
                validateEmbeddingDimensions(vector.embedding());
                statement.setString(1, vector.id());
                statement.setLong(2, vector.workspaceId());
                statement.setLong(3, vector.documentId());
                statement.setString(4, vector.chunkId());
                statement.setInt(5, vector.chunkSequence());
                statement.setString(6, vector.headingPath());
                statement.setString(7, vector.content());
                statement.setString(8, PgVectorSqlSupport.toVectorLiteral(vector.embedding()));
                statement.setTimestamp(9, Timestamp.from(vector.createdAt().toInstant()));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private VectorSearchResult mapSearchResult(ResultSet resultSet) throws SQLException {
        return new VectorSearchResult(
                resultSet.getString("chunk_id"),
                resultSet.getLong("document_id"),
                resultSet.getInt("chunk_sequence"),
                resultSet.getString("heading_path"),
                resultSet.getString("content"),
                resultSet.getDouble("score")
        );
    }

    private void validateEmbeddingDimensions(float[] embedding) {
        if (embedding.length != embeddingDimensions) {
            throw new IllegalArgumentException(
                    "向量维度必须为 " + embeddingDimensions + "，实际为 " + embedding.length);
        }
    }
}
