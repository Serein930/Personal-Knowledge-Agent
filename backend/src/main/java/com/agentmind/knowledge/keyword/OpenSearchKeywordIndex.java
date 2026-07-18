package com.agentmind.knowledge.keyword;

import com.agentmind.document.chunk.DocumentChunk;
import com.agentmind.knowledge.model.dto.KnowledgeSearchResultResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final ObjectMapper objectMapper;
    private final AtomicBoolean indexReady = new AtomicBoolean();

    @Autowired
    public OpenSearchKeywordIndex(OpenSearchKeywordIndexProperties properties, ObjectMapper objectMapper) {
        RestClient.Builder builder = RestClient.builder().baseUrl(properties.getBaseUrl());
        if (StringUtils.hasText(properties.getUsername())) {
            String credentials = properties.getUsername() + ":" + properties.getPassword();
            builder.defaultHeader(HttpHeaders.AUTHORIZATION,
                    "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
        }
        this.client = builder.build();
        this.indexName = properties.getIndexName();
        this.objectMapper = objectMapper;
    }

    /** 为不启动 Spring 容器的集成测试保留便捷构造方式。 */
    public OpenSearchKeywordIndex(OpenSearchKeywordIndexProperties properties) {
        this(properties, new ObjectMapper());
    }

    @Override
    public void replaceDocumentChunks(Long workspaceId, Long documentId, List<DocumentChunk> chunks) {
        replaceDocuments(List.of(new KeywordIndexDocument(workspaceId, documentId, chunks)));
    }

    @Override
    public void replaceDocuments(List<KeywordIndexDocument> documents) {
        if (documents.isEmpty()) {
            return;
        }
        ensureIndex();
        documents.forEach(document -> deleteDocumentChunks(document.workspaceId(), document.documentId()));
        String requestBody = bulkRequestBody(documents);
        if (!requestBody.isBlank()) {
            JsonNode response = client.post().uri("/_bulk")
                    .contentType(MediaType.parseMediaType("application/x-ndjson"))
                    // String 消息转换器可能按照系统代码页发送正文；NDJSON 必须显式使用 UTF-8，
                    // 否则中文文档会被 OpenSearch 判定为非法 UTF-8 并造成部分条目写入失败。
                    .body(requestBody.getBytes(StandardCharsets.UTF_8))
                    .retrieve().body(JsonNode.class);
            if (response != null && response.path("errors").asBoolean(false)) {
                IllegalStateException failure = bulkIndexFailure(response);
                // OpenSearch 批量接口可能部分成功。失败时清理本批次所有目标文档，
                // 防止失败任务留下不完整片段并参与后续检索。
                documents.forEach(document ->
                        deleteDocumentChunks(document.workspaceId(), document.documentId()));
                throw failure;
            }
        }
        client.post().uri("/{index}/_refresh", indexName).retrieve().toBodilessEntity();
    }

    @Override
    public void deleteDocumentChunks(Long workspaceId, Long documentId) {
        ensureIndex();
        // replacement 与删除接口返回后，调用方会立即写入或查询；refresh=true 保证旧片段
        // 在本次操作完成前对后续搜索不可见，避免同一文档新旧版本短暂混合。
        client.post().uri("/{index}/_delete_by_query?conflicts=proceed&refresh=true", indexName)
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

    /**
     * 提取首个失败条目的安全摘要，保留排障信息但不把用户正文写入异常或日志。
     */
    private IllegalStateException bulkIndexFailure(JsonNode response) {
        int failedCount = 0;
        String firstFailure = "OpenSearch 未返回具体失败原因";
        for (JsonNode item : response.path("items")) {
            JsonNode operation = item.elements().hasNext() ? item.elements().next() : null;
            if (operation == null || operation.path("status").asInt(200) < 300) {
                continue;
            }
            failedCount++;
            if (failedCount == 1) {
                JsonNode error = operation.path("error");
                JsonNode cause = error.path("caused_by");
                String type = error.path("type").asText("unknown");
                String reason = cause.path("reason").asText(error.path("reason").asText("unknown"));
                firstFailure = "文档编号=" + operation.path("_id").asText("unknown")
                        + "，类型=" + type + "，原因=" + reason;
            }
        }
        return new IllegalStateException(
                "OpenSearch 批量索引失败：失败条目=" + failedCount + "，首个失败=" + firstFailure);
    }

    /** 使用 Jackson 生成每一行 JSON，避免正文中的引号或换行破坏批量协议。 */
    private String bulkRequestBody(List<KeywordIndexDocument> documents) {
        StringBuilder body = new StringBuilder();
        try {
            for (KeywordIndexDocument document : documents) {
                for (DocumentChunk chunk : document.chunks()) {
                    body.append(objectMapper.writeValueAsString(Map.of(
                            "index", Map.of("_index", indexName, "_id",
                                    documentKey(document.workspaceId(), chunk.id()))))).append('\n');
                    body.append(objectMapper.writeValueAsString(Map.of(
                            "workspaceId", document.workspaceId(),
                            "documentId", document.documentId(),
                            "chunkId", chunk.id(),
                            "chunkSequence", chunk.sequence(),
                            "headingPath", chunk.headingPath(),
                            "content", chunk.content()
                    ))).append('\n');
                }
            }
            return body.toString();
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("生成 OpenSearch 批量索引请求失败", exception);
        }
    }
}
