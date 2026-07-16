package com.agentmind.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentmind.document.model.DocumentSourceType;
import com.agentmind.document.repository.JdbcDocumentMetadataRepository;
import com.agentmind.workspace.model.WorkspaceMemberRole;
import com.agentmind.workspace.repository.JdbcKnowledgeWorkspaceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Flyway、用户、成员关系和文档元数据的 PostgreSQL 集成测试。 */
@Testcontainers(disabledWithoutDocker = true)
@EnabledIfEnvironmentVariable(named = "AGENTMIND_RUN_PRODUCTION_INTEGRATION", matches = "true")
class IdentityPersistenceIntegrationTests {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("agentmind_identity")
            .withUsername("agentmind")
            .withPassword("agentmind_test_password");

    private static JdbcUserAccountRepository userRepository;
    private static JdbcKnowledgeWorkspaceRepository workspaceRepository;
    private static JdbcDocumentMetadataRepository documentRepository;

    @BeforeAll
    static void initializeDatabase() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        userRepository = new JdbcUserAccountRepository(jdbcTemplate);
        workspaceRepository = new JdbcKnowledgeWorkspaceRepository(jdbcTemplate);
        documentRepository = new JdbcDocumentMetadataRepository(
                jdbcTemplate, new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void shouldPersistUserWorkspaceMembershipAndDocumentMetadata() {
        var user = userRepository.create("integration-user", "集成用户",
                "integration@example.com", "bcrypt-placeholder");
        var workspace = workspaceRepository.createOwnedWorkspace(user.id(), "集成知识空间", "权限测试");
        var document = documentRepository.create(user.id(), workspace.getId(), "生产安全文档",
                DocumentSourceType.MARKDOWN, null, "security.md", List.of("安全", "JWT"));
        documentRepository.markSucceeded(document.id(), "workspace/documents/security.md",
                "text/markdown", 128, 3);

        assertThat(workspaceRepository.findMemberRole(workspace.getId(), user.id()))
                .contains(WorkspaceMemberRole.OWNER);
        assertThat(documentRepository.findByWorkspaceIdAndId(workspace.getId(), document.id()))
                .get()
                .satisfies(saved -> {
                    assertThat(saved.ownerUserId()).isEqualTo(user.id());
                    assertThat(saved.storageKey()).isEqualTo("workspace/documents/security.md");
                    assertThat(saved.tags()).containsExactly("安全", "JWT");
                    assertThat(saved.chunkCount()).isEqualTo(3);
                });
    }
}
