package com.agentmind.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentmind.agent.confirmation.repository.AgentToolConfirmationRepository;
import com.agentmind.agent.confirmation.repository.JdbcAgentToolConfirmationRepository;
import com.agentmind.chat.memory.repository.ChatMemoryRepository;
import com.agentmind.chat.memory.repository.redis.RedisChatMemoryRepository;
import com.agentmind.chat.repository.JdbcRagModelCallObservationRepository;
import com.agentmind.chat.repository.RagModelCallObservationRepository;
import com.agentmind.evaluation.repository.JdbcRagEvaluationDatasetRepository;
import com.agentmind.evaluation.repository.RagEvaluationDatasetRepository;
import com.agentmind.knowledge.repository.PgVectorStore;
import com.agentmind.knowledge.vector.VectorStore;
import com.agentmind.study.flashcard.repository.JdbcStudyFlashcardRepository;
import com.agentmind.study.flashcard.repository.StudyFlashcardRepository;
import com.agentmind.user.repository.JdbcUserAccountRepository;
import com.agentmind.user.repository.JdbcUserWorkspacePreferenceRepository;
import com.agentmind.user.repository.UserAccountRepository;
import com.agentmind.user.repository.UserWorkspacePreferenceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 使用真实 PostgreSQL、pgvector 和 Redis 验证 local profile 的最终 Bean 装配。
 *
 * <p>该测试不执行业务写入，只验证 Spring 条件装配结果。数据库由 Flyway 从空库初始化，
 * Redis 使用一次性容器，测试结束后不会污染开发者本机数据。</p>
 */
@SpringBootTest(properties = {
        // 本测试只验证持久化 Bean，不需要创建依赖付费模型的 ChatClient。
        "spring.ai.chat.client.enabled=false"
})
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
@EnabledIfEnvironmentVariable(named = "AGENTMIND_RUN_PERSISTENCE_PROFILE_INTEGRATION", matches = "true")
class PersistentRepositoryProfileIntegrationTests {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("agentmind_profile")
            .withUsername("agentmind")
            .withPassword("agentmind_test_password");

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void registerInfrastructureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private UserWorkspacePreferenceRepository preferenceRepository;

    @Autowired
    private AgentToolConfirmationRepository confirmationRepository;

    @Autowired
    private StudyFlashcardRepository flashcardRepository;

    @Autowired
    private RagModelCallObservationRepository observationRepository;

    @Autowired
    private RagEvaluationDatasetRepository evaluationDatasetRepository;

    @Autowired
    private ChatMemoryRepository chatMemoryRepository;

    @Autowired
    private VectorStore vectorStore;

    @Test
    void localProfileShouldSelectOnlyPersistentRepositoryAdapters() {
        assertThat(userAccountRepository).isInstanceOf(JdbcUserAccountRepository.class);
        assertThat(preferenceRepository).isInstanceOf(JdbcUserWorkspacePreferenceRepository.class);
        assertThat(confirmationRepository).isInstanceOf(JdbcAgentToolConfirmationRepository.class);
        assertThat(flashcardRepository).isInstanceOf(JdbcStudyFlashcardRepository.class);
        assertThat(observationRepository).isInstanceOf(JdbcRagModelCallObservationRepository.class);
        assertThat(evaluationDatasetRepository).isInstanceOf(JdbcRagEvaluationDatasetRepository.class);
        assertThat(chatMemoryRepository).isInstanceOf(RedisChatMemoryRepository.class);
        assertThat(vectorStore).isInstanceOf(PgVectorStore.class);

        // 所有内存 Repository 类名都遵循统一约定；local 上下文中不允许出现任何一个。
        assertThat(applicationContext.getBeansOfType(Object.class).values())
                .noneMatch(bean -> isAgentMindInMemoryRepository(AopUtils.getTargetClass(bean)));
    }

    private boolean isAgentMindInMemoryRepository(Class<?> beanType) {
        return beanType.getPackageName().startsWith("com.agentmind")
                && beanType.getSimpleName().startsWith("InMemory")
                && beanType.getSimpleName().endsWith("Repository");
    }
}
