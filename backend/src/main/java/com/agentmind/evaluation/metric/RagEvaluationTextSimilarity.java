package com.agentmind.evaluation.metric;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 面向中英文混合知识资料的确定性文本相似度组件。
 *
 * <p>英文和数字按单词切分，连续中文按双字片段切分，再计算 Jaccard 相似度。该实现不是语义模型，
 * 但完全离线、结果稳定，适合自动测试和实验基线；后续可通过独立端口替换为模型裁判。</p>
 */
@Component
public class RagEvaluationTextSimilarity {

    public double similarity(String left, String right) {
        Set<String> leftTokens = tokens(left);
        Set<String> rightTokens = tokens(right);
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) {
            return 0;
        }
        Set<String> intersection = new LinkedHashSet<>(leftTokens);
        intersection.retainAll(rightTokens);
        Set<String> union = new LinkedHashSet<>(leftTokens);
        union.addAll(rightTokens);
        return intersection.size() * 1.0 / union.size();
    }

    Set<String> tokens(String text) {
        Set<String> result = new LinkedHashSet<>();
        if (text == null || text.isBlank()) {
            return result;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        StringBuilder word = new StringBuilder();
        StringBuilder chinese = new StringBuilder();
        normalized.codePoints().forEach(codePoint -> {
            if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN) {
                flushWord(word, result);
                chinese.appendCodePoint(codePoint);
                return;
            }
            flushChinese(chinese, result);
            if (Character.isLetterOrDigit(codePoint)) {
                word.appendCodePoint(codePoint);
            } else {
                flushWord(word, result);
            }
        });
        flushWord(word, result);
        flushChinese(chinese, result);
        return result;
    }

    private static void flushWord(StringBuilder word, Set<String> result) {
        if (word.length() >= 2) {
            result.add(word.toString());
        }
        word.setLength(0);
    }

    private static void flushChinese(StringBuilder chinese, Set<String> result) {
        if (chinese.isEmpty()) {
            return;
        }
        int[] codePoints = chinese.codePoints().toArray();
        if (codePoints.length == 1) {
            result.add(new String(codePoints, 0, 1));
        } else {
            for (int index = 0; index < codePoints.length - 1; index++) {
                result.add(new String(codePoints, index, 2));
            }
        }
        chinese.setLength(0);
    }
}
