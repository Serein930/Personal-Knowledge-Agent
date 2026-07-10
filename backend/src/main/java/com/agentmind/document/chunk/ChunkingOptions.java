package com.agentmind.document.chunk;

/**
 * 文本切分参数。
 */
public record ChunkingOptions(
        int maxChars,
        int overlapChars
) {

    public static ChunkingOptions defaults() {
        return new ChunkingOptions(1200, 150);
    }
}
