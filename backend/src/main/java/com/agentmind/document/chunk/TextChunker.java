package com.agentmind.document.chunk;

import com.agentmind.document.model.DocumentSourceType;
import java.util.List;

/**
 * Splits extracted text into RAG-ready chunks.
 */
public interface TextChunker {

    List<DocumentChunk> chunk(Long documentId, DocumentSourceType sourceType, String text);
}
