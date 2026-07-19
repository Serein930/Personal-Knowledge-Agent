package com.agentmind.knowledge.vector;

import com.agentmind.knowledge.model.KnowledgeVector;
import com.agentmind.knowledge.model.VectorSearchResult;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * 关系数据库与数据库向量扩展接入前使用的内存向量库。
 *
 * <p>该实现刻意保持简单，但职责与真实向量库一致：按文档替换向量、摄取失败时删除向量、
 * 并且只在指定知识空间内检索。</p>
 */
@Repository
@ConditionalOnProperty(prefix = "agentmind.vector-store", name = "type", havingValue = "memory", matchIfMissing = true)
public class InMemoryVectorStore implements VectorStore {

    private final Map<String, KnowledgeVector> vectors = new ConcurrentHashMap<>();

    @Override
    public void replaceDocumentVectors(Long workspaceId, Long documentId, Collection<KnowledgeVector> newVectors) {
        deleteDocumentVectors(workspaceId, documentId);
        for (KnowledgeVector vector : newVectors) {
            vectors.put(vector.id(), vector);
        }
    }

    @Override
    public void deleteDocumentVectors(Long workspaceId, Long documentId) {
        vectors.entrySet().removeIf(entry ->
                Objects.equals(entry.getValue().workspaceId(), workspaceId)
                        && Objects.equals(entry.getValue().documentId(), documentId));
    }

    @Override
    public List<VectorSearchResult> search(Long workspaceId, float[] queryEmbedding, int topK) {
        return search(workspaceId, queryEmbedding, topK, Set.of());
    }

    @Override
    public List<VectorSearchResult> search(
            Long workspaceId,
            float[] queryEmbedding,
            int topK,
            Set<Long> documentIds
    ) {
        return vectors.values().stream()
                .filter(vector -> Objects.equals(vector.workspaceId(), workspaceId))
                .filter(vector -> documentIds == null || documentIds.isEmpty()
                        || documentIds.contains(vector.documentId()))
                .map(vector -> toResult(vector, cosineSimilarity(queryEmbedding, vector.embedding())))
                .filter(result -> result.score() > 0)
                .sorted(Comparator.comparingDouble(VectorSearchResult::score).reversed())
                .limit(topK)
                .toList();
    }

    private VectorSearchResult toResult(KnowledgeVector vector, double score) {
        return new VectorSearchResult(
                vector.chunkId(),
                vector.documentId(),
                vector.chunkSequence(),
                vector.headingPath(),
                vector.content(),
                score
        );
    }

    private double cosineSimilarity(float[] left, float[] right) {
        if (left.length != right.length) {
            return 0;
        }
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int index = 0; index < left.length; index++) {
            dot += left[index] * right[index];
            leftNorm += left[index] * left[index];
            rightNorm += right[index] * right[index];
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return 0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }
}
