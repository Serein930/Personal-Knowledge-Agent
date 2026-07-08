package com.agentmind.document.chunk;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentmind.document.model.DocumentSourceType;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarkdownAwareTextChunkerTests {

    private final MarkdownAwareTextChunker chunker = new MarkdownAwareTextChunker();

    @Test
    void chunkShouldPreserveMarkdownHeadingPath() {
        List<DocumentChunk> chunks = chunker.chunk(
                10L,
                DocumentSourceType.MARKDOWN,
                """
                # RAG

                Retrieval augmented generation should keep source references.

                ## Chunking

                Chunks should be stable and include metadata.
                """
        );

        assertThat(chunks).hasSize(2);
        assertThat(chunks.getFirst().headingPath()).isEqualTo("RAG");
        assertThat(chunks.get(1).headingPath()).isEqualTo("Chunking");
    }
}
