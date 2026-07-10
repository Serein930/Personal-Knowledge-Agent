package com.agentmind.knowledge.vector;

import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 用于本地开发的无外部依赖向量生成实现。
 *
 * <p>这不是真实语言模型向量。它会把词元哈希到固定维度向量并做归一化，
 * 足够在接入付费或本地向量模型前验证索引、知识空间隔离和检索排序。</p>
 */
@Component
public class DeterministicEmbeddingClient implements EmbeddingClient {

    private static final int DIMENSIONS = 128;

    @Override
    public float[] embed(String text) {
        float[] vector = new float[DIMENSIONS];
        if (!StringUtils.hasText(text)) {
            return vector;
        }

        String[] tokens = text.toLowerCase().split("[^\\p{IsAlphabetic}\\p{IsDigit}]+");
        for (String token : tokens) {
            if (!StringUtils.hasText(token)) {
                continue;
            }
            int index = positiveHash(token) % DIMENSIONS;
            vector[index] += 1.0f;
        }
        normalize(vector);
        return vector;
    }

    private int positiveHash(String token) {
        int hash = 0;
        for (byte value : token.getBytes(StandardCharsets.UTF_8)) {
            hash = 31 * hash + value;
        }
        return hash & 0x7fffffff;
    }

    private void normalize(float[] vector) {
        double sum = 0;
        for (float value : vector) {
            sum += value * value;
        }
        if (sum == 0) {
            return;
        }
        float length = (float) Math.sqrt(sum);
        for (int index = 0; index < vector.length; index++) {
            vector[index] = vector[index] / length;
        }
    }
}
