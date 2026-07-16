package com.agentmind.knowledge.keyword;

import com.agentmind.document.chunk.DocumentChunk;
import java.util.List;

/** 一次关键词索引替换所需的完整文档快照。 */
public record KeywordIndexDocument(
        Long workspaceId,
        Long documentId,
        List<DocumentChunk> chunks
) {
    public KeywordIndexDocument {
        chunks = chunks == null ? List.of() : List.copyOf(chunks);
    }
}
