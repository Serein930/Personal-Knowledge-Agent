package com.agentmind.knowledge.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentmind.document.chunk.DocumentChunk;
import com.agentmind.knowledge.keyword.InMemoryBm25KeywordIndex;
import com.agentmind.knowledge.model.dto.KnowledgeSearchResultResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 验证 BM25 知识空间隔离与 RRF 双路名次融合。 */
class HybridRetrievalTests {

    @Test
    void bm25ShouldRecallExactTermOnlyInsideCurrentWorkspace() {
        InMemoryBm25KeywordIndex index = new InMemoryBm25KeywordIndex();
        index.replaceDocumentChunks(1L, 10L, List.of(chunk("a", 10L, "虚拟线程 pinning 诊断")));
        index.replaceDocumentChunks(2L, 20L, List.of(chunk("b", 20L, "另一个空间的 pinning 文档")));

        List<KnowledgeSearchResultResponse> results = index.search(1L, "pinning", 5);

        assertThat(results).extracting(KnowledgeSearchResultResponse::documentId).containsExactly(10L);
        assertThat(results.getFirst().score()).isPositive();
    }

    @Test
    void rrfShouldPromoteChunkAppearingInBothRankings() {
        ReciprocalRankFusion fusion = new ReciprocalRankFusion();
        KnowledgeSearchResultResponse shared = result("shared", 1L, 0.6);
        List<KnowledgeSearchResultResponse> fused = fusion.fuse(
                List.of(result("vector-only", 2L, 0.99), shared),
                List.of(result("keyword-only", 3L, 12.0), shared),
                60,
                3
        );

        assertThat(fused.getFirst().chunkId()).isEqualTo("shared");
        assertThat(fused.getFirst().score()).isBetween(0.0, 1.0);
    }

    private DocumentChunk chunk(String id, Long documentId, String content) {
        return new DocumentChunk(id, documentId, 0, "测试", content, 0, content.length());
    }

    private KnowledgeSearchResultResponse result(String chunkId, Long documentId, double score) {
        return new KnowledgeSearchResultResponse(chunkId, documentId, 0, "测试", chunkId, score);
    }
}
