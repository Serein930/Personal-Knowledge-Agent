package com.agentmind.document.chunk;

import com.agentmind.document.model.DocumentSourceType;
import java.util.List;

/**
 * 将提取后的文本切分为可检索增强生成使用的知识片段。
 */
public interface TextChunker {

    List<DocumentChunk> chunk(Long documentId, DocumentSourceType sourceType, String text);
}
