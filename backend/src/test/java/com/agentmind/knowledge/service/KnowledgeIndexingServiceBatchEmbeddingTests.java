package com.agentmind.knowledge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentmind.document.chunk.DocumentChunk;
import com.agentmind.knowledge.keyword.KeywordIndex;
import com.agentmind.knowledge.model.KnowledgeVector;
import com.agentmind.knowledge.vector.EmbeddingClient;
import com.agentmind.knowledge.vector.VectorStore;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class KnowledgeIndexingServiceBatchEmbeddingTests {

    @Test
    void shouldGenerateAllChunkVectorsInOneBatchCall() {
        EmbeddingClient embeddingClient = mock(EmbeddingClient.class);
        VectorStore vectorStore = mock(VectorStore.class);
        KeywordIndex keywordIndex = mock(KeywordIndex.class);
        List<DocumentChunk> chunks = List.of(
                new DocumentChunk("chunk-1", 9L, 0, "标题一", "第一段", 0, 3),
                new DocumentChunk("chunk-2", 9L, 1, "标题二", "第二段", 3, 6)
        );
        when(embeddingClient.embedAll(List.of("第一段", "第二段")))
                .thenReturn(List.of(new float[]{1, 0}, new float[]{0, 1}));
        KnowledgeIndexingService service = new KnowledgeIndexingService(
                embeddingClient, vectorStore, keywordIndex);

        service.indexChunks(7L, 9L, chunks);

        verify(embeddingClient).embedAll(List.of("第一段", "第二段"));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<KnowledgeVector>> vectorsCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(vectorStore).replaceDocumentVectors(org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq(9L), vectorsCaptor.capture());
        assertThat(vectorsCaptor.getValue())
                .extracting(vector -> vector.embedding()[0])
                .containsExactly(1F, 0F);
        verify(keywordIndex).replaceDocumentChunks(7L, 9L, chunks);
    }
}
