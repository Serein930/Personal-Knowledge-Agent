package com.agentmind.document.repository;

import com.agentmind.document.chunk.DocumentChunk;
import java.util.List;

/** 文档片段仓储端口，保存解析结果并服务于原文片段查看。 */
public interface DocumentChunkRepository {

    void replaceDocumentChunks(Long ownerUserId, Long workspaceId, Long documentId, List<DocumentChunk> chunks);

    List<DocumentChunk> findAllByDocumentId(Long documentId);

    void deleteAllByDocumentId(Long documentId);
}
