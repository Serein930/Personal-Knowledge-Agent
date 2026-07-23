package com.agentmind.study.flashcard.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 不依赖外部模型的原子问答卡生成器。
 *
 * <p>该实现优先从定义、作用、组成和因果关系中构造具体问题。每张卡只使用一个短事实，
 * 不再把整个文本片段直接作为答案。它既是离线默认实现，也是模型结构化输出失败时的安全兜底。</p>
 */
@Component
public class LocalDocumentFlashcardCandidateGenerator implements DocumentFlashcardCandidateGenerator {

    private static final int MAX_ATOMIC_TEXT_LENGTH = 260;
    private static final Pattern UNIT_SEPARATOR = Pattern.compile(
            "(?:\\r?\\n)+|\\s*[●•▪◆]\\s*|(?<=[。！？!?；;])\\s*"
    );
    private static final List<String> PREDICATES = List.of(
            "是", "指", "用于", "用来", "决定", "负责", "包括", "分为", "通过", "表示", "能够", "可以"
    );

    @Override
    public List<GeneratedDocumentFlashcard> generate(
            List<DocumentFlashcardSource> sources,
            int requestedCount
    ) {
        List<GeneratedDocumentFlashcard> generated = new ArrayList<>();
        Set<String> normalizedQuestions = new LinkedHashSet<>();
        for (DocumentFlashcardSource source : sources) {
            for (String unit : atomicUnits(source.content())) {
                GeneratedDocumentFlashcard card = toCard(source, unit);
                String deduplicationKey = normalizeForDeduplication(card.question());
                if (normalizedQuestions.add(deduplicationKey)) {
                    generated.add(card);
                }
                if (generated.size() >= requestedCount) {
                    return List.copyOf(generated);
                }
            }
        }
        return List.copyOf(generated);
    }

    private List<String> atomicUnits(String content) {
        if (!StringUtils.hasText(content)) {
            return List.of();
        }
        List<String> units = new ArrayList<>();
        for (String rawUnit : UNIT_SEPARATOR.split(content.trim())) {
            String normalized = normalize(rawUnit);
            if (normalized.length() < 8 || looksLikeStandaloneQuestion(normalized)) {
                continue;
            }
            splitLongUnit(normalized, units);
        }
        return units;
    }

    private void splitLongUnit(String unit, List<String> target) {
        String remaining = unit;
        while (remaining.length() > MAX_ATOMIC_TEXT_LENGTH) {
            int boundary = findBoundary(remaining, MAX_ATOMIC_TEXT_LENGTH);
            String part = normalize(remaining.substring(0, boundary));
            if (part.length() >= 8) {
                target.add(part);
            }
            remaining = normalize(remaining.substring(boundary));
        }
        if (remaining.length() >= 8) {
            target.add(remaining);
        }
    }

    private int findBoundary(String value, int preferredBoundary) {
        int lowerBound = Math.max(80, preferredBoundary / 2);
        for (int index = preferredBoundary; index >= lowerBound; index--) {
            char character = value.charAt(index - 1);
            if (Character.isWhitespace(character) || "，、：,:".indexOf(character) >= 0) {
                return index;
            }
        }
        return preferredBoundary;
    }

    private GeneratedDocumentFlashcard toCard(DocumentFlashcardSource source, String fact) {
        String topic = StringUtils.hasText(source.topic()) ? source.topic().trim() : source.documentTitle();
        String question = buildSpecificQuestion(topic, fact);
        return new GeneratedDocumentFlashcard(
                source.documentId(),
                source.chunkId(),
                limit(topic, 100),
                question,
                ensureSentence(limit(fact, MAX_ATOMIC_TEXT_LENGTH)),
                "答案提取自《" + source.documentTitle() + "》的“" + limit(topic, 80) + "”片段。"
        );
    }

    private String buildSpecificQuestion(String topic, String fact) {
        int colonIndex = firstIndex(fact, "：", ":");
        if (colonIndex >= 2 && colonIndex <= 50) {
            return ensureQuestion(limit(cleanSubject(fact.substring(0, colonIndex)), 80) + "是什么");
        }
        for (String predicate : PREDICATES) {
            int index = fact.indexOf(predicate);
            if (index >= 2 && index <= 50) {
                String subject = cleanSubject(fact.substring(0, index));
                if (StringUtils.hasText(subject)) {
                    return switch (predicate) {
                        case "用于", "用来" -> ensureQuestion(limit(subject, 80) + "用于解决什么问题");
                        case "决定" -> ensureQuestion(limit(subject, 80) + "决定什么");
                        case "负责" -> ensureQuestion(limit(subject, 80) + "负责什么");
                        case "包括" -> ensureQuestion(limit(subject, 80) + "包括哪些内容");
                        case "分为" -> ensureQuestion(limit(subject, 80) + "可以分为哪些部分");
                        case "通过" -> ensureQuestion(limit(subject, 80) + "通过什么方式实现");
                        case "表示" -> ensureQuestion(limit(subject, 80) + "表示什么");
                        case "能够", "可以" -> ensureQuestion(limit(subject, 80) + "具有什么能力");
                        default -> ensureQuestion(limit(subject, 80) + "是什么");
                    };
                }
            }
        }
        String focus = fact.length() <= 32 ? fact : fact.substring(0, 32);
        return ensureQuestion("在“" + limit(topic, 60) + "”中，如何理解“" + cleanSubject(focus) + "”");
    }

    private boolean looksLikeStandaloneQuestion(String value) {
        return value.endsWith("?") || value.endsWith("？");
    }

    private String cleanSubject(String value) {
        return value.replaceFirst("^[\\d一二三四五六七八九十]+[.、)）\\s]*", "")
                .replaceAll("^[，。；;：:\\s]+|[，。；;：:\\s]+$", "")
                .trim();
    }

    private String normalize(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }

    private String normalizeForDeduplication(String value) {
        return value.replaceAll("[\\s，。！？!?：:、“”‘’《》]", "").toLowerCase(Locale.ROOT);
    }

    private int firstIndex(String value, String... candidates) {
        int result = -1;
        for (String candidate : candidates) {
            int index = value.indexOf(candidate);
            if (index >= 0 && (result < 0 || index < result)) {
                result = index;
            }
        }
        return result;
    }

    private String ensureQuestion(String value) {
        String normalized = value.replaceAll("[。！？!?]+$", "").trim();
        return normalized + "？";
    }

    private String ensureSentence(String value) {
        String normalized = value.trim();
        return "。！？!?".indexOf(normalized.charAt(normalized.length() - 1)) >= 0
                ? normalized : normalized + "。";
    }

    private String limit(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
