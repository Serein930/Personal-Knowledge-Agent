package com.agentmind.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.RegexPatternTypeFilter;
import org.springframework.util.ClassUtils;

/**
 * 审计所有内存、JDBC 和 Redis Repository 的条件装配契约。
 *
 * <p>测试通过类路径扫描自动包含后续新增的 Repository。只要新增适配器沿用项目命名规范却遗漏条件注解，
 * 或误用了其他模块的配置键，构建就会失败，从而阻止生产环境同时装配两套实现或静默回退内存。</p>
 */
class RepositoryPersistenceConditionContractTests {

    private static final Pattern PERSISTENCE_REPOSITORY_PATTERN = Pattern.compile(
            "com\\.agentmind\\..*\\.(InMemory|Jdbc|Redis).*Repository"
    );

    @Test
    void everyPersistenceRepositoryShouldDeclareItsModuleSelectionCondition() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new RegexPatternTypeFilter(PERSISTENCE_REPOSITORY_PATTERN));
        Set<BeanDefinition> candidates = scanner.findCandidateComponents("com.agentmind");

        assertThat(candidates).isNotEmpty();
        for (BeanDefinition candidate : candidates) {
            Class<?> repositoryClass = ClassUtils.forName(
                    candidate.getBeanClassName(),
                    Thread.currentThread().getContextClassLoader()
            );
            assertRepositoryCondition(repositoryClass, expectedCondition(repositoryClass));
        }
    }

    private void assertRepositoryCondition(Class<?> repositoryClass, ExpectedCondition expected) {
        ConditionalOnProperty condition = repositoryClass.getAnnotation(ConditionalOnProperty.class);

        assertThat(condition)
                .as("%s 必须声明 @ConditionalOnProperty", repositoryClass.getName())
                .isNotNull();
        assertThat(condition.prefix()).isEqualTo(expected.prefix());
        assertThat(condition.name()).contains(expected.name());
        assertThat(condition.havingValue()).isEqualTo(expected.havingValue());
        assertThat(condition.matchIfMissing()).isEqualTo(expected.matchIfMissing());
    }

    private ExpectedCondition expectedCondition(Class<?> repositoryClass) {
        String packageName = repositoryClass.getPackageName();
        String simpleName = repositoryClass.getSimpleName();

        if (packageName.contains(".knowledge.outbox.repository")) {
            return new ExpectedCondition("agentmind.knowledge-index.outbox", "enabled", "true", false);
        }

        String prefix;
        String name = "store";
        if (packageName.contains(".chat.memory.repository")) {
            prefix = "agentmind.chat.memory";
        } else if (packageName.contains(".chat.repository")) {
            prefix = "agentmind.rag";
            name = "observation-store";
        } else if (packageName.contains(".evaluation.repository")) {
            prefix = "agentmind.evaluation";
        } else if (packageName.contains(".agent.") || packageName.contains(".study.")) {
            prefix = "agentmind.agent.persistence";
        } else if (packageName.contains(".user.repository")
                || packageName.contains(".workspace.repository")
                || packageName.contains(".document.repository")) {
            prefix = "agentmind.core.persistence";
        } else {
            throw new AssertionError("发现尚未归类的持久化 Repository：" + repositoryClass.getName());
        }

        if (simpleName.startsWith("InMemory")) {
            return new ExpectedCondition(prefix, name, "memory", true);
        }
        if (simpleName.startsWith("Jdbc")) {
            return new ExpectedCondition(prefix, name, "jdbc", false);
        }
        if (simpleName.startsWith("Redis")) {
            return new ExpectedCondition(prefix, name, "redis", false);
        }
        throw new AssertionError("无法识别 Repository 适配器类型：" + repositoryClass.getName());
    }

    private record ExpectedCondition(
            String prefix,
            String name,
            String havingValue,
            boolean matchIfMissing
    ) {
    }
}
