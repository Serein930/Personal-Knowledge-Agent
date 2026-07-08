package com.agentmind.document.model.dto;

/**
 * Chunk response used by the temporary chunk preview endpoint.
 *
 * <p>This DTO lets the frontend or developer verify parser/chunker behavior before chunks are persisted in a
 * database/vector store.</p>
 */
public record DocumentChunkResponse(
        String id,
        Long documentId,
        int sequence,
        String headingPath,
        String content,
        int charStart,
        int charEnd
) {
}
