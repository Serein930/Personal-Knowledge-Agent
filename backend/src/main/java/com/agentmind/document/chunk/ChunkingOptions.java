package com.agentmind.document.chunk;

/**
 * Controls text chunk size and overlap.
 */
public record ChunkingOptions(
        int maxChars,
        int overlapChars
) {

    public static ChunkingOptions defaults() {
        return new ChunkingOptions(1200, 150);
    }
}
