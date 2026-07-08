package com.agentmind.knowledge.repository;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Minimal DataSource configuration for the pgvector adapter.
 *
 * <p>The project does not enable a database by default. This configuration creates a simple DriverManager-backed
 * DataSource only when `agentmind.vector-store.type=pgvector`. It keeps the current memory mode startup clean while
 * giving the next stage a concrete switch point for PostgreSQL.</p>
 */
@Configuration
@ConditionalOnProperty(prefix = "agentmind.vector-store", name = "type", havingValue = "pgvector")
public class PgVectorDataSourceConfig {

    @Bean
    @ConditionalOnMissingBean(DataSource.class)
    public DataSource pgVectorDataSource(
            @Value("${spring.datasource.url:}") String url,
            @Value("${spring.datasource.username:}") String username,
            @Value("${spring.datasource.password:}") String password
    ) {
        if (!StringUtils.hasText(url)) {
            throw new IllegalStateException("spring.datasource.url is required when pgvector vector store is enabled");
        }
        return new DriverManagerBackedDataSource(url, username, password);
    }

    private static class DriverManagerBackedDataSource implements DataSource {

        private final String url;
        private final String username;
        private final String password;
        private PrintWriter logWriter;
        private int loginTimeout;

        private DriverManagerBackedDataSource(String url, String username, String password) {
            this.url = url;
            this.username = username;
            this.password = password;
        }

        @Override
        public Connection getConnection() throws SQLException {
            if (StringUtils.hasText(username)) {
                return DriverManager.getConnection(url, username, password);
            }
            return DriverManager.getConnection(url);
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return DriverManager.getConnection(url, username, password);
        }

        @Override
        public PrintWriter getLogWriter() {
            return logWriter;
        }

        @Override
        public void setLogWriter(PrintWriter logWriter) {
            this.logWriter = logWriter;
        }

        @Override
        public void setLoginTimeout(int seconds) {
            this.loginTimeout = seconds;
            DriverManager.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() {
            return loginTimeout;
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
            throw new SQLException("DataSource cannot unwrap to " + iface.getName());
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return iface.isInstance(this);
        }
    }
}
