package com.agentmind.knowledge.outbox.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 防止生产 Outbox 与临时内存索引误配，导致重启后关键词数据丢失。 */
@Component
@ConditionalOnProperty(prefix = "agentmind.knowledge-index.outbox", name = "enabled", havingValue = "true")
public class KnowledgeIndexOutboxConfigurationValidator {

    private final String vectorStoreType;
    private final String keywordIndexType;

    public KnowledgeIndexOutboxConfigurationValidator(
            @Value("${agentmind.vector-store.type:memory}") String vectorStoreType,
            @Value("${agentmind.keyword-index.type:memory}") String keywordIndexType
    ) {
        this.vectorStoreType = vectorStoreType;
        this.keywordIndexType = keywordIndexType;
    }

    @PostConstruct
    public void validate() {
        if (!"pgvector".equalsIgnoreCase(vectorStoreType)) {
            throw new IllegalStateException("开启知识索引 Outbox 时，向量库必须配置为 pgvector");
        }
        if (!"opensearch".equalsIgnoreCase(keywordIndexType)) {
            throw new IllegalStateException("开启知识索引 Outbox 时，关键词索引必须配置为 opensearch");
        }
    }
}
