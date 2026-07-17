package com.agentmind.common.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 正式环境配置防误启校验器。
 *
 * <p>校验只判断配置是否满足生产边界，不输出任何秘密原文。通过在容器接收流量前快速失败，
 * 避免以关闭认证、内存仓储或本地文件存储的状态误发布到正式环境。</p>
 */
@Component
@Profile("production")
public class ProductionConfigurationValidator implements SmartInitializingSingleton {

    private final Environment environment;

    public ProductionConfigurationValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterSingletonsInstantiated() {
        List<String> violations = new ArrayList<>();
        requireOneOf(violations, "agentmind.security.mode", "local-jwt", "oidc");
        requireValue(violations, "agentmind.core.persistence.store", "jdbc");
        requireValue(violations, "agentmind.rate-limit.mode", "redis");
        requireValue(violations, "agentmind.rate-limit.fail-open", "false");
        requireValue(violations, "agentmind.rate-limit.client-ip-header", "X-Forwarded-For");
        requireValue(violations, "agentmind.web.trusted-proxy.enabled", "true");
        requireValue(violations, "agentmind.web.trusted-proxy.require-https", "true");
        requireText(violations, "agentmind.web.trusted-proxy.cidrs");
        requireValue(violations, "server.forward-headers-strategy", "none");
        requireValue(violations, "agentmind.storage.type", "minio");
        requireValue(violations, "agentmind.vector-store.type", "pgvector");
        requireValue(violations, "agentmind.keyword-index.type", "opensearch");
        requireValue(violations, "agentmind.knowledge-index.outbox.enabled", "true");
        requireValue(violations, "agentmind.chat.memory.store", "redis");
        requireText(violations, "spring.datasource.url");
        requireText(violations, "spring.datasource.username");
        requireSecret(violations, "spring.datasource.password");
        requireSecret(violations, "spring.data.redis.password");
        requireSecret(violations, "agentmind.storage.minio.access-key");
        requireSecret(violations, "agentmind.storage.minio.secret-key");
        requireText(violations, "agentmind.keyword-index.opensearch.base-url");
        requireText(violations, "agentmind.keyword-index.opensearch.username");
        requireSecret(violations, "agentmind.keyword-index.opensearch.password");
        requireSecureOrigin(violations);
        requireSecuritySecret(violations);

        if (!violations.isEmpty()) {
            throw new IllegalStateException("正式环境配置验收失败：" + String.join("；", violations));
        }
    }

    private void requireSecuritySecret(List<String> violations) {
        String mode = property("agentmind.security.mode");
        if ("local-jwt".equalsIgnoreCase(mode)) {
            String secret = property("agentmind.security.jwt-secret");
            if (secret == null || secret.length() < 32) {
                violations.add("本地 JWT 签名秘密必须至少 32 个字符");
            }
        } else if ("oidc".equalsIgnoreCase(mode)) {
            requireText(violations, "agentmind.security.issuer-uri");
            requireText(violations, "agentmind.security.audience");
        }
    }

    private void requireSecureOrigin(List<String> violations) {
        String origin = property("agentmind.web.allowed-origin");
        if (origin == null || !origin.startsWith("https://") || origin.contains("localhost")) {
            violations.add("跨域来源必须是非本机 HTTPS 地址");
        }
    }

    private void requireValue(List<String> violations, String name, String expected) {
        if (!expected.equalsIgnoreCase(property(name))) {
            violations.add(name + " 必须配置为 " + expected);
        }
    }

    private void requireOneOf(List<String> violations, String name, String... expectedValues) {
        String actual = property(name);
        for (String expected : expectedValues) {
            if (expected.equalsIgnoreCase(actual)) {
                return;
            }
        }
        violations.add(name + " 必须配置为 " + String.join(" 或 ", expectedValues));
    }

    private void requireText(List<String> violations, String name) {
        if (isBlank(property(name))) {
            violations.add(name + " 不能为空");
        }
    }

    private void requireSecret(List<String> violations, String name) {
        String value = property(name);
        String normalized = value == null ? "" : value.toLowerCase(java.util.Locale.ROOT);
        if (isBlank(value) || normalized.equals("agentmind_dev_password")
                || normalized.equals("changeme") || normalized.contains("placeholder")) {
            violations.add(name + " 必须由外部秘密注入且不能使用示例值");
        }
    }

    private String property(String name) {
        return environment.getProperty(name);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
