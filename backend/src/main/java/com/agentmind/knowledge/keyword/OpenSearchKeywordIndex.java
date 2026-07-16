package com.agentmind.knowledge.keyword;

import com.agentmind.document.chunk.DocumentChunk;
import com.agentmind.knowledge.model.dto.KnowledgeSearchResultResponse;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * OpenSearch 原生 BM25 适配器。
 *
 * <p>每次写入前按知识空间和文档删除旧片段，再写入新片段；查询必须带知识空间精确过滤，
 * 防止关键词召回绕过私有数据边界。索引映射由适配器首次访问时幂等创建。</p>
 */
@Repository
@ConditionalOnProperty(prefix = "agentmind.keyword-index", name = "type", havingValue = "opensearch")
public class OpenSearchKeywordIndex implements KeywordIndex {

    private final RestClient client;
    private final String indexName;
    private final AtomicBoolean indexReady = new AtomicBoolean();

    public OpenSearchKeywordIndex(OpenSearchKeywordIndexProperties properties) {
        RestClient.Builder builder = RestClient.builder().baseUrl(properties.getBaseUrl());
        if (StringUtils.hasText(properties.getUsername())) {
            String credentials = properties.getUsername() + ":" + properties.getPassword();
            builder.defaultHeader(HttpHeaders.AUTHORIZATION,
                    "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
        }
        this.client = builder.build();
        this.indexName = properties.getIndexName();
    }

    @Override
    public void replaceDocumentChunks(Long workspaceId, Long documentId, List<DocumentChunk> chunks) {
        ensureIndex();
        deleteDocumentChunks(workspaceId, documentId);
        for (DocumentChunk chunk : chunks) {
            client.put().uri("/{index}/_doc/{id}", indexName, documentKey(workspaceId, chunk.id()))
                    .body(Map.of(
                            "workspaceId", workspaceId,
                            "documentId", documentId,
                            "chunkId", chunk.id(),
                            "chunkSequence", chunk.sequence(),
                            "headingPath", chunk.headingPath(),
                            "content", chunk.content()
                    )).retrieve().toBodilessEntity();
        }
        client.post().uri("/{index}/_refresh", indexName).retrieve().toBodilessEntity();
    }

    @Override
    public void deleteDocumentChunks(Long workspaceId, Long documentId) {
        ensureIndex();
        client.post().uri("/{index}/_delete_by_query?conflicts=proceed", indexName)
                .body(Map.of("query", Map.of("bool", Map.of("filter", List.of(
                        Map.of("term", Map.of("workspaceId", workspaceId)),
                        Map.of("term", Map.of("documentId", documentId))
                ))))).retrieve().toBodilessEntity();
    }

    @Override
    public List<KnowledgeSearchResultResponse> search(Long workspaceId, String query, int topK) {
        ensureIndex();
        JsonNode response = client.post().uri("/{index}/_search", indexName)
                .body(Map.of(
                        "size", topK,
                        "query", Map.of("bool", Map.of(
                                "filter", List.of(Map.of("term", Map.of("workspaceId", workspaceId))),
                                "must", List.of(Map.of("multi_match", Map.of(
                                        "query", query,
                                        "fields", List.of("content", "headingPath^1.5"),
                                        "type", "best_fields"
                                )))
                        ))
                )).retrieve().body(JsonNode.class);
        if (response == null || !response.path("hits").path("hits").isArray()) {
            return List.of();
        }
        return java.util.stream.StreamSupport.stream(
                        response.path("hits").path("hits").spliterator(), false
                ).map(this::toResponse).toList();
    }

    private synchronized void ensureIndex() {
        if (indexReady.get()) {
            return;
        }
        try {
            client.put().uri("/{index}", indexName).body(Map.of(
                    "mappings", Map.of("properties", Map.of(
                            "workspaceId", Map.of("type", "long"),
                            "documentId", Map.of("type", "long"),
                            "chunkId", Map.of("type", "keyword"),
                            "chunkSequence", Map.of("type", "integer"),
                            "headingPath", Map.of("type", "text"),
                            "content", Map.of("type", "text")
                    ))
            )).retrieve().toBodilessEntity();
        } catch (org.springframework.web.client.HttpClientErrorException.BadRequest exception) {
            // 多实例首次启动可能同时建索引；资源已存在属于成功的幂等结果，其他映射错误继续抛出。
            if (!exception.getResponseBodyAsString().contains("resource_already_exists_exception")) {
                throw exception;
            }
        }
        indexReady.set(true);
    }

    private KnowledgeSearchResultResponse toResponse(JsonNode hit) {
        JsonNode source = hit.path("_source");
        return new KnowledgeSearchResultResponse(
                source.path("chunkId").asText(),
                source.path("documentId").asLong(),
                source.path("chunkSequence").asInt(),
                source.path("headingPath").asText(),
                source.path("content").asText(),
                hit.path("_score").asDouble()
        );
    }

    private String documentKey(Long workspaceId, String chunkId) {
        return workspaceId + "-" + chunkId;
    }
}
