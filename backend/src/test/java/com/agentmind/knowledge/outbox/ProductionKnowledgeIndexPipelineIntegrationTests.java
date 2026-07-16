package com.agentmind.knowledge.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentmind.document.chunk.DocumentChunk;
import com.agentmind.knowledge.keyword.OpenSearchKeywordIndex;
import com.agentmind.knowledge.keyword.OpenSearchKeywordIndexProperties;
import com.agentmind.knowledge.outbox.config.KnowledgeIndexOutboxProperties;
import com.agentmind.knowledge.outbox.repository.JdbcKnowledgeIndexOutboxRepository;
import com.agentmind.knowledge.outbox.service.JdbcKnowledgeIndexTransactionExecutor;
import com.agentmind.knowledge.outbox.service.KnowledgeIndexOutboxWorker;
import com.agentmind.knowledge.outbox.service.KnowledgeIndexChangePublisher;
import com.agentmind.knowledge.outbox.service.OutboxKnowledgeIndexChangePublisher;
import com.agentmind.knowledge.repository.PgVectorStore;
import com.agentmind.knowledge.service.KnowledgeIndexingService;
import com.agentmind.knowledge.vector.DeterministicEmbeddingClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * PostgreSQL、pgvector、事务 Outbox 与 OpenSearch 的生产链路集成测试。
 *
 * <p>该测试需要拉取容器镜像，因此默认不进入普通单元测试；CI 或本地显式设置
 * {@code AGENTMIND_RUN_PRODUCTION_INTEGRATION=true} 后运行。</p>
 */
@Testcontainers(disabledWithoutDocker = true)
@EnabledIfEnvironmentVariable(named = "AGENTMIND_RUN_PRODUCTION_INTEGRATION", matches = "true")
class ProductionKnowledgeIndexPipelineIntegrationTests {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("agentmind")
            .withUsername("agentmind")
            .withPassword("agentmind_test_password");

    @Container
    private static final GenericContainer<?> OPENSEARCH = new GenericContainer<>(
            DockerImageName.parse("opensearchproject/opensearch:2.17.1"))
            .withEnv("discovery.type", "single-node")
            .withEnv("plugins.security.disabled", "true")
            .withEnv("OPENSEARCH_JAVA_OPTS", "-Xms512m -Xmx512m")
            .withExposedPorts(9200)
            .waitingFor(Wait.forHttp("/_cluster/health").forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(3)));

    private static JdbcTemplate jdbcTemplate;
    private static DriverManagerDataSource dataSource;
    private static JdbcKnowledgeIndexOutboxRepository outboxRepository;
    private static OpenSearchKeywordIndex keywordIndex;
    private static KnowledgeIndexOutboxProperties outboxProperties;

    @BeforeAll
    static void initializeInfrastructure() {
        dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbcTemplate = new JdbcTemplate(dataSource);

        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        outboxRepository = new JdbcKnowledgeIndexOutboxRepository(jdbcTemplate, objectMapper);
        OpenSearchKeywordIndexProperties searchProperties = new OpenSearchKeywordIndexProperties();
        searchProperties.setBaseUrl("http://" + OPENSEARCH.getHost() + ":" + OPENSEARCH.getMappedPort(9200));
        searchProperties.setIndexName("agentmind-production-pipeline-test");
        keywordIndex = new OpenSearchKeywordIndex(searchProperties, objectMapper);

        outboxProperties = new KnowledgeIndexOutboxProperties();
        outboxProperties.setBatchSize(20);
        outboxProperties.setLeaseDuration(Duration.ofSeconds(30));
        outboxProperties.setInstanceId("integration-worker");
    }

    @Test
    void shouldCommitVectorAndOutboxThenEventuallyIndexIntoOpenSearch() {
        KnowledgeIndexingService indexingService = createIndexingService();
        List<DocumentChunk> chunks = List.of(
                new DocumentChunk("9001-0", 9001L, 0, "事务消息", "Outbox 保证最终一致性", 0, 14),
                new DocumentChunk("9001-1", 9001L, 1, "批量索引", "OpenSearch 使用批量写入", 15, 30)
        );

        indexingService.indexChunks(99L, 9001L, chunks);

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from knowledge_vector_chunks where workspace_id = 99 and document_id = 9001",
                Long.class)).isEqualTo(2L);
        assertThat(outboxRepository.statistics(99L).pending()).isEqualTo(1L);

        createWorker("integration-worker").processOnce();

        assertThat(outboxRepository.statistics(99L).completed()).isEqualTo(1L);
        assertThat(keywordIndex.search(99L, "OpenSearch", 5))
                .extracting(result -> result.documentId())
                .contains(9001L);
    }

    @Test
    void twoInstancesShouldClaimDifferentRows() {
        OffsetDateTime now = OffsetDateTime.now();
        for (long documentId = 9101; documentId <= 9103; documentId++) {
            outboxRepository.enqueue("lease-test-" + documentId, 100L, documentId,
                    com.agentmind.knowledge.outbox.model.KnowledgeIndexOutboxOperation.DELETE,
                    new com.agentmind.knowledge.outbox.model.KnowledgeIndexOutboxPayload(List.of()), now);
        }

        var first = outboxRepository.claimBatch("instance-a", now, now.plusSeconds(30), 2);
        var second = outboxRepository.claimBatch("instance-b", now, now.plusSeconds(30), 2);

        HashSet<Long> claimedIds = new HashSet<>(first.stream().map(event -> event.id()).toList());
        assertThat(second).allMatch(event -> !claimedIds.contains(event.id()));
    }

    @Test
    void shouldRollbackVectorAndOutboxWhenTransactionFails() {
        OutboxKnowledgeIndexChangePublisher delegate = new OutboxKnowledgeIndexChangePublisher(outboxRepository);
        KnowledgeIndexChangePublisher failingPublisher = new KnowledgeIndexChangePublisher() {
            @Override
            public void publishUpsert(Long workspaceId, Long documentId, List<DocumentChunk> chunks) {
                delegate.publishUpsert(workspaceId, documentId, chunks);
                throw new IllegalStateException("模拟事务提交前故障");
            }

            @Override
            public void publishDelete(Long workspaceId, Long documentId) {
                delegate.publishDelete(workspaceId, documentId);
                throw new IllegalStateException("模拟事务提交前故障");
            }
        };
        KnowledgeIndexingService service = new KnowledgeIndexingService(
                new DeterministicEmbeddingClient(), new PgVectorStore(jdbcTemplate, 128), failingPublisher,
                new JdbcKnowledgeIndexTransactionExecutor(new DataSourceTransactionManager(dataSource)));

        assertThatThrownBy(() -> service.indexChunks(101L, 9201L, List.of(
                new DocumentChunk("9201-0", 9201L, 0, "事务回滚", "不应留下半成品", 0, 8))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from knowledge_vector_chunks where workspace_id = 101 and document_id = 9201",
                Long.class)).isZero();
        assertThat(outboxRepository.statistics(101L).pending()).isZero();
    }

    private static KnowledgeIndexingService createIndexingService() {
        PgVectorStore vectorStore = new PgVectorStore(jdbcTemplate, 128);
        OutboxKnowledgeIndexChangePublisher publisher = new OutboxKnowledgeIndexChangePublisher(outboxRepository);
        return new KnowledgeIndexingService(
                new DeterministicEmbeddingClient(),
                vectorStore,
                publisher,
                new JdbcKnowledgeIndexTransactionExecutor(new DataSourceTransactionManager(dataSource))
        );
    }

    private static KnowledgeIndexOutboxWorker createWorker(String instanceId) {
        outboxProperties.setInstanceId(instanceId);
        return new KnowledgeIndexOutboxWorker(outboxRepository, keywordIndex, outboxProperties,
                new SimpleMeterRegistry());
    }
}
