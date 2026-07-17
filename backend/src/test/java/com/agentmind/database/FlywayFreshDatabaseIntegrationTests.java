package com.agentmind.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 验证全新 PostgreSQL 数据库仅执行 Flyway 就能获得完整正式表结构。
 *
 * <p>该测试使用一次性 pgvector 容器，不复用开发数据库，也不读取 {@code db/schema} 手工执行脚本。
 * 测试同时执行第二次迁移，用于证明迁移历史稳定且不会重复修改已完成的数据库。</p>
 */
@Testcontainers(disabledWithoutDocker = true)
@EnabledIfEnvironmentVariable(named = "AGENTMIND_RUN_FLYWAY_INTEGRATION", matches = "true")
class FlywayFreshDatabaseIntegrationTests {

    private static final List<String> REQUIRED_TABLES = List.of(
            "agent_tool_call_audits",
            "agent_tool_confirmations",
            "app_user",
            "conversation_learning_summaries",
            "daily_study_plans",
            "daily_study_task_cards",
            "daily_study_task_events",
            "daily_study_tasks",
            "fsrs_parameter_optimization_jobs",
            "fsrs_user_profile_versions",
            "fsrs_user_profiles",
            "knowledge_document",
            "knowledge_index_outbox",
            "knowledge_notes",
            "knowledge_vector_chunks",
            "knowledge_workspace",
            "learning_topic_profiles",
            "rag_evaluation_dataset_versions",
            "rag_evaluation_datasets",
            "rag_evaluation_jobs",
            "rag_model_call_observations",
            "study_flashcard_fsrs_states",
            "study_flashcard_reviews",
            "study_flashcards",
            "study_review_session_items",
            "study_review_sessions",
            "workspace_member"
    );

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("agentmind_flyway")
            .withUsername("agentmind")
            .withPassword("agentmind_test_password");

    @Test
    void freshDatabaseShouldContainCompleteProductionSchemaAfterFlywayMigration() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway flyway = Flyway.configure().dataSource(dataSource).load();

        MigrateResult firstMigration = flyway.migrate();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        assertThat(firstMigration.success).isTrue();
        assertThat(migrationVersions(jdbcTemplate)).containsExactly("1", "2", "3", "4", "5", "6", "7");
        assertThat(publicTables(jdbcTemplate)).containsAll(REQUIRED_TABLES);
        assertThat(extensionNames(jdbcTemplate)).contains("vector");
        assertThat(vectorColumnType(jdbcTemplate)).isEqualTo("vector(128)");
        assertThat(constraintNames(jdbcTemplate)).contains(
                "ck_agent_tool_confirmation_status",
                "uk_knowledge_notes_request",
                "uk_study_flashcard_reviews_request",
                "ck_rag_evaluation_job_status",
                "uk_knowledge_index_outbox_event_key"
        );

        // 已完成的数据库再次启动时不应重复执行任何版本，避免生产发布期间产生结构漂移。
        MigrateResult secondMigration = flyway.migrate();
        assertThat(secondMigration.migrationsExecuted).isZero();
    }

    private List<String> migrationVersions(JdbcTemplate jdbcTemplate) {
        return jdbcTemplate.queryForList(
                "select version from flyway_schema_history where success = true order by installed_rank",
                String.class
        );
    }

    private List<String> publicTables(JdbcTemplate jdbcTemplate) {
        return jdbcTemplate.queryForList(
                "select tablename from pg_tables where schemaname = 'public' order by tablename",
                String.class
        );
    }

    private List<String> extensionNames(JdbcTemplate jdbcTemplate) {
        return jdbcTemplate.queryForList("select extname from pg_extension order by extname", String.class);
    }

    private String vectorColumnType(JdbcTemplate jdbcTemplate) {
        return jdbcTemplate.queryForObject("""
                select format_type(attribute.atttypid, attribute.atttypmod)
                from pg_attribute attribute
                join pg_class relation on relation.oid = attribute.attrelid
                join pg_namespace namespace on namespace.oid = relation.relnamespace
                where namespace.nspname = 'public'
                  and relation.relname = 'knowledge_vector_chunks'
                  and attribute.attname = 'embedding'
                  and attribute.attnum > 0
                """, String.class);
    }

    private List<String> constraintNames(JdbcTemplate jdbcTemplate) {
        return jdbcTemplate.queryForList("""
                select constraint_name
                from information_schema.table_constraints
                where constraint_schema = 'public'
                order by constraint_name
                """, String.class);
    }
}
