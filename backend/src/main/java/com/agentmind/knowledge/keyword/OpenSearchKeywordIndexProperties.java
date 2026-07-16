package com.agentmind.knowledge.keyword;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** OpenSearch 关键词索引连接与索引配置。 */
@Component
@ConfigurationProperties(prefix = "agentmind.keyword-index.opensearch")
public class OpenSearchKeywordIndexProperties {

    private String baseUrl = "http://localhost:9200";
    private String indexName = "agentmind-knowledge-chunks";
    private String username = "";
    private String password = "";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getIndexName() {
        return indexName;
    }

    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
