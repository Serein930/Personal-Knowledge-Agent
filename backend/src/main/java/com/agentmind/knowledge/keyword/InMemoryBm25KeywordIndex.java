package com.agentmind.knowledge.keyword;

import com.agentmind.document.chunk.DocumentChunk;
import com.agentmind.knowledge.model.dto.KnowledgeSearchResultResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * 可重复测试的内存 BM25 关键词索引。
 *
 * <p>英文按单词切分，连续中文按单字和二元组切分。评分采用 Okapi BM25 的常用参数
 * k1=1.2、b=0.75，适合验证双路召回和排序语义，但不承担生产容量。</p>
 */
@Repository
@ConditionalOnProperty(
        prefix = "agentmind.keyword-index",
        name = "type",
        havingValue = "memory",
        matchIfMissing = true
)
public class InMemoryBm25KeywordIndex implements KeywordIndex {

    private static final double K1 = 1.2;
    private static final double B = 0.75;
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[a-zA-Z0-9_]+|[\\p{IsHan}]+");

    private final Map<String, IndexedChunk> chunks = new ConcurrentHashMap<>();

    @Override
    public synchronized void replaceDocumentChunks(
            Long workspaceId,
            Long documentId,
            List<DocumentChunk> newChunks
    ) {
        deleteDocumentChunks(workspaceId, documentId);
        for (DocumentChunk chunk : newChunks) {
            List<String> tokens = tokenize(chunk.headingPath() + " " + chunk.content());
            chunks.put(key(workspaceId, chunk.id()), new IndexedChunk(
                    workspaceId, documentId, chunk.id(), chunk.sequence(), chunk.headingPath(), chunk.content(),
                    frequencies(tokens), tokens.size()
            ));
        }
    }

    @Override
    public synchronized void deleteDocumentChunks(Long workspaceId, Long documentId) {
        chunks.entrySet().removeIf(entry -> Objects.equals(entry.getValue().workspaceId(), workspaceId)
                && Objects.equals(entry.getValue().documentId(), documentId));
    }

    @Override
    public List<KnowledgeSearchResultResponse> search(Long workspaceId, String query, int topK) {
        List<IndexedChunk> corpus = chunks.values().stream()
                .filter(chunk -> Objects.equals(chunk.workspaceId(), workspaceId))
                .toList();
        if (corpus.isEmpty()) {
            return List.of();
        }
        List<String> queryTokens = tokenize(query).stream().distinct().toList();
        double averageLength = corpus.stream().mapToInt(IndexedChunk::length).average().orElse(1.0);
        return corpus.stream()
                .map(chunk -> new ScoredChunk(chunk, score(chunk, corpus, queryTokens, averageLength)))
                .filter(value -> value.score() > 0)
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed()
                        .thenComparing(value -> value.chunk().chunkId()))
                .limit(topK)
                .map(this::toResponse)
                .toList();
    }

    private double score(
            IndexedChunk chunk,
            List<IndexedChunk> corpus,
            List<String> queryTokens,
            double averageLength
    ) {
        double score = 0;
        for (String token : queryTokens) {
            int frequency = chunk.termFrequencies().getOrDefault(token, 0);
            if (frequency == 0) {
                continue;
            }
            long containingDocuments = corpus.stream()
                    .filter(value -> value.termFrequencies().containsKey(token)).count();
            double inverseDocumentFrequency = Math.log(
                    1.0 + (corpus.size() - containingDocuments + 0.5) / (containingDocuments + 0.5)
            );
            double normalizedFrequency = frequency * (K1 + 1.0)
                    / (frequency + K1 * (1.0 - B + B * chunk.length() / averageLength));
            score += inverseDocumentFrequency * normalizedFrequency;
        }
        return score;
    }

    private List<String> tokenize(String value) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(value == null ? "" : value.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String token = matcher.group();
            if (!containsHan(token)) {
                tokens.add(token);
                continue;
            }
            token.codePoints().forEach(codePoint -> tokens.add(new String(Character.toChars(codePoint))));
            int[] codePoints = token.codePoints().toArray();
            for (int index = 0; index + 1 < codePoints.length; index++) {
                tokens.add(new String(codePoints, index, 2));
            }
        }
        return tokens;
    }

    private boolean containsHan(String value) {
        return value.codePoints().anyMatch(codePoint -> Character.UnicodeScript.of(codePoint)
                == Character.UnicodeScript.HAN);
    }

    private Map<String, Integer> frequencies(List<String> tokens) {
        Map<String, Integer> frequencies = new HashMap<>();
        tokens.forEach(token -> frequencies.merge(token, 1, Integer::sum));
        return Map.copyOf(frequencies);
    }

    private KnowledgeSearchResultResponse toResponse(ScoredChunk value) {
        IndexedChunk chunk = value.chunk();
        return new KnowledgeSearchResultResponse(
                chunk.chunkId(), chunk.documentId(), chunk.sequence(), chunk.headingPath(), chunk.content(), value.score()
        );
    }

    private String key(Long workspaceId, String chunkId) {
        return workspaceId + ":" + chunkId;
    }

    private record IndexedChunk(
            Long workspaceId,
            Long documentId,
            String chunkId,
            int sequence,
            String headingPath,
            String content,
            Map<String, Integer> termFrequencies,
            int length
    ) {
    }

    private record ScoredChunk(IndexedChunk chunk, double score) {
    }
}
