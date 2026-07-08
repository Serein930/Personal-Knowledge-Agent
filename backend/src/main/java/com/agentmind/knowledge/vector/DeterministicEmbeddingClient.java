package com.agentmind.knowledge.vector;

import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Dependency-free embedding implementation for local development.
 *
 * <p>This is not a real language model embedding. It hashes tokens into a fixed-size vector and normalizes the
 * result, which is good enough to verify indexing, workspace isolation and retrieval ranking before integrating a
 * paid or local embedding model.</p>
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
