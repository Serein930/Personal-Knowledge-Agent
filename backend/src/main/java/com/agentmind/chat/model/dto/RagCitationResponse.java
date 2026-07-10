package com.agentmind.chat.model.dto;

/**
 * Citation returned with a RAG response.
 *
 * <p>Each citation points to one retrieved document chunk. The chunk id is a string because current chunk ids are
 * stable logical ids such as `10-0`, and pgvector records keep that same value for source tracing.</p>
 */
public record RagCitationResponse(
        int index,
        Long documentId,
        String documentTitle,
        String chunkId,
        int chunkSequence,
        String headingPath,
        String excerpt,
        double score
) {
}
