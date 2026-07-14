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
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * 数据库向量扩展适配器使用的轻量数据源配置。
 *
 * <p>项目默认不启用数据库。只有当向量库类型切换为数据库向量扩展时，该配置才会创建基于驱动管理器的简单数据源。
 * 这样既能保持内存模式启动轻量，也给后续关系数据库接入留下明确切换点。</p>
 */
@Configuration
@ConditionalOnExpression("'${agentmind.vector-store.type:memory}' == 'pgvector' "
        + "or '${agentmind.agent.persistence.store:memory}' == 'jdbc'")
public class PgVectorDataSourceConfig {

    @Bean
    @ConditionalOnMissingBean(DataSource.class)
    public DataSource pgVectorDataSource(
            @Value("${spring.datasource.url:}") String url,
            @Value("${spring.datasource.username:}") String username,
            @Value("${spring.datasource.password:}") String password
    ) {
        if (!StringUtils.hasText(url)) {
            throw new IllegalStateException("启用数据库向量库或智能体 JDBC 持久化时必须配置 spring.datasource.url");
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
            throw new SQLFeatureNotSupportedException("驱动管理器不暴露父级日志器");
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) {
                return iface.cast(this);
            }
            throw new SQLException("数据源不能解包为 " + iface.getName());
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return iface.isInstance(this);
        }
    }
}
