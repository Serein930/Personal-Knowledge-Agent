package com.agentmind.agent.confirmation.config;

import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 智能体写工具 JDBC 基础设施配置。
 *
 * <p>只有显式选择 JDBC 存储时才创建数据库访问模板与事务管理器。默认内存模式不会尝试连接数据库。</p>
 */
@Configuration
@ConditionalOnProperty(prefix = "agentmind.agent.persistence", name = "store", havingValue = "jdbc")
public class AgentJdbcTransactionConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JdbcTemplate agentMindJdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean(PlatformTransactionManager.class)
    public PlatformTransactionManager agentMindTransactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
