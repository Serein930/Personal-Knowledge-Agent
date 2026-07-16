package com.agentmind.knowledge.keyword;

import com.agentmind.document.chunk.DocumentChunk;
import com.agentmind.knowledge.model.dto.KnowledgeSearchResultResponse;
import java.util.List;

/**
 * 关键词索引端口。
 *
 * <p>业务层只依赖该契约，默认内存 BM25 与 OpenSearch 原生 BM25 可以通过配置平滑替换。</p>
 */
public interface KeywordIndex {

    void replaceDocumentChunks(Long workspaceId, Long documentId, List<DocumentChunk> chunks);

    /**
     * 批量替换多个文档。默认实现保持所有适配器兼容，OpenSearch 适配器会覆盖为真正的批量请求。
     */
    default void replaceDocuments(List<KeywordIndexDocument> documents) {
        documents.forEach(document -> replaceDocumentChunks(
                document.workspaceId(), document.documentId(), document.chunks()));
    }

    void deleteDocumentChunks(Long workspaceId, Long documentId);

    List<KnowledgeSearchResultResponse> search(Long workspaceId, String query, int topK);
}
