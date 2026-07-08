package com.agentmind.document.chunk;

/**
 * Chunk generated from extracted document text.
 *
 * <p>The model keeps source offsets and heading path so future vector records can preserve citation metadata.
 * These fields are important for RAG answers because they make it possible to show where a retrieved answer came
 * from.</p>
 */
public record DocumentChunk(
        String id,
        Long documentId,
        int sequence,
        String headingPath,
        String content,
        int charStart,
        int charEnd
) {
}
