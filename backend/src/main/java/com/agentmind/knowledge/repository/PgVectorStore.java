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
 * PostgreSQL + pgvector implementation of {@link VectorStore}.
 *
 * <p>This adapter uses plain JDBC so the project can keep the default memory mode lightweight. It is enabled only
 * when `agentmind.vector-store.type=pgvector` and a {@link DataSource} bean exists. A later infrastructure stage can
 * add Spring JDBC, Flyway and connection-pool configuration without changing the application-facing
 * {@link VectorStore} interface.</p>
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
            throw new IllegalStateException("Failed to replace pgvector document vectors", exception);
        }
    }

    @Override
    public void deleteDocumentVectors(Long workspaceId, Long documentId) {
        try (Connection connection = dataSource.getConnection()) {
            deleteDocumentVectors(connection, workspaceId, documentId);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to delete pgvector document vectors", exception);
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
            throw new IllegalStateException("Failed to search pgvector chunks", exception);
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
                    "Embedding dimensions must be " + embeddingDimensions + ", but was " + embedding.length);
        }
    }
}
