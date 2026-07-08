package com.agentmind.knowledge.vector;

import com.agentmind.knowledge.model.KnowledgeVector;
import com.agentmind.knowledge.model.VectorSearchResult;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/**
 * In-memory vector store used before PostgreSQL + pgvector is introduced.
 *
 * <p>The implementation is intentionally simple but keeps the same responsibilities as a real vector store:
 * replace vectors per document, delete vectors on failed ingestion, and search only inside the requested workspace.</p>
 */
@Repository
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
        return vectors.values().stream()
                .filter(vector -> Objects.equals(vector.workspaceId(), workspaceId))
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
