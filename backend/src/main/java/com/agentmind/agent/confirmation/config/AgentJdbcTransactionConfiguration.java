package com.agentmind.agent.confirmation.config;

import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 项目共享 JDBC 基础设施配置。
 *
 * <p>智能体写工具或固定评估任一模块显式选择 JDBC 存储时，创建共享数据库访问模板与事务管理器。
 * 默认内存模式不会尝试连接数据库。</p>
 */
@Configuration
@ConditionalOnExpression("'${agentmind.agent.persistence.store:memory}' == 'jdbc' "
        + "or '${agentmind.evaluation.store:memory}' == 'jdbc'")
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
