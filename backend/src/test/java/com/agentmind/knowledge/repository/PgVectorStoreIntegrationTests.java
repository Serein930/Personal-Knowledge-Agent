package com.agentmind.knowledge.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentmind.knowledge.model.KnowledgeVector;
import com.agentmind.knowledge.model.VectorSearchResult;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * 关系数据库与数据库向量扩展适配器的手动集成测试。
 *
 * <p>该测试默认禁用，因为它依赖本地容器数据库。运行前需要先启动数据库容器，
 * 再临时移除禁用标记，并在开发工具或构建工具中执行该测试类。它验证的是与内存适配器相同的向量库契约。</p>
 */
@Tag("pgvector")
@Disabled("手动测试：需要先启动数据库向量扩展容器")
class PgVectorStoreIntegrationTests {

    private static final String JDBC_URL = "jdbc:postgresql://localhost:5432/agentmind";
    private static final String USERNAME = "agentmind";
    private static final String PASSWORD = "agentmind_dev_password";

    private final PgVectorStore vectorStore = new PgVectorStore(
            new DriverManagerDataSource(JDBC_URL, USERNAME, PASSWORD),
            128
    );

    @Test
    void replaceAndSearchShouldUsePgVectorSimilarityWithinWorkspace() throws Exception {
        ensureSchemaExists();
        float[] javaEmbedding = unitVectorAt(1);
        float[] ragEmbedding = unitVectorAt(2);

        vectorStore.replaceDocumentVectors(100L, 200L, List.of(
                new KnowledgeVector(
                        "manual-100-200-0",
                        100L,
                        200L,
                        "200-0",
                        0,
                        "Java",
                        "Thread pools reuse workers for backend tasks.",
                        javaEmbedding,
                        OffsetDateTime.now()
                ),
                new KnowledgeVector(
                        "manual-100-200-1",
                        100L,
                        200L,
                        "200-1",
                        1,
                        "RAG",
                        "RAG retrieves private knowledge chunks before generation.",
                        ragEmbedding,
                        OffsetDateTime.now()
                )
        ));

        List<VectorSearchResult> results = vectorStore.search(100L, javaEmbedding, 1);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().chunkId()).isEqualTo("200-0");
        assertThat(results.getFirst().score()).isGreaterThan(0.99);
    }

    private void ensureSchemaExists() throws SQLException {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD);
                Statement statement = connection.createStatement()) {
            statement.execute("create extension if not exists vector");
            statement.execute("""
                    create table if not exists knowledge_vector_chunks (
                        id varchar(128) primary key,
                        workspace_id bigint not null,
                        document_id bigint not null,
                        chunk_id varchar(128) not null,
                        chunk_sequence integer not null,
                        heading_path varchar(512),
                        content text not null,
                        embedding vector(128) not null,
                        created_at timestamp with time zone not null default now()
                    )
                    """);
        }
    }

    private float[] unitVectorAt(int index) {
        float[] embedding = new float[128];
        embedding[index] = 1.0f;
        return embedding;
    }

    private static class DriverManagerDataSource implements DataSource {

        private final String url;
        private final String username;
        private final String password;

        private DriverManagerDataSource(String url, String username, String password) {
            this.url = url;
            this.username = username;
            this.password = password;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return DriverManager.getConnection(url, username, password);
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return DriverManager.getConnection(url, username, password);
        }

        @Override
        public PrintWriter getLogWriter() {
            return DriverManager.getLogWriter();
        }

        @Override
        public void setLogWriter(PrintWriter out) {
            DriverManager.setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) {
            DriverManager.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() {
            return DriverManager.getLoginTimeout();
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException("DriverManager does not expose a parent logger");
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) {
                return iface.cast(this);
            }
            throw new SQLException("Cannot unwrap DataSource to " + iface.getName());
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return iface.isInstance(this);
        }
    }
}
