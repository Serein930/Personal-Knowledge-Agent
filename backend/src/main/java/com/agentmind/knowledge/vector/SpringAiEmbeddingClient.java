package com.agentmind.knowledge.vector;

import com.agentmind.knowledge.vector.config.EmbeddingProperties;
import com.agentmind.knowledge.vector.observability.EmbeddingCallObservation;
import com.agentmind.knowledge.vector.observability.EmbeddingObservability;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 基于 Spring AI {@link EmbeddingModel} 的真实向量模型适配器。
 *
 * <p>适配器负责批量切分、有限重试、维度校验和费用观测。它不记录输入文本，
 * 也不在失败时退回确定性向量，避免同一向量库混入不可比较的两种向量空间。</p>
 */
@Component
@ConditionalOnProperty(prefix = "agentmind.embedding", name = "provider", havingValue = "spring-ai")
public class SpringAiEmbeddingClient implements EmbeddingClient {

    private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000L);

    private final EmbeddingModel embeddingModel;
    private final EmbeddingProperties properties;
    private final EmbeddingObservability observability;

    public SpringAiEmbeddingClient(
            EmbeddingModel embeddingModel,
            EmbeddingProperties properties,
            EmbeddingObservability observability
    ) {
        this.embeddingModel = embeddingModel;
        this.properties = properties;
        this.observability = observability;
    }

    @Override
    public float[] embed(String text) {
        return embedAll(List.of(text)).getFirst();
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {
        if (texts == null) {
            throw new IllegalArgumentException("向量输入列表不能为空");
        }
        if (texts.isEmpty()) {
            return List.of();
        }
        texts.forEach(text -> {
            if (!StringUtils.hasText(text)) {
                throw new IllegalArgumentException("向量输入文本不能为空");
            }
        });

        List<float[]> vectors = new ArrayList<>(texts.size());
        for (int start = 0; start < texts.size(); start += properties.getBatchSize()) {
            int end = Math.min(start + properties.getBatchSize(), texts.size());
            vectors.addAll(embedBatch(List.copyOf(texts.subList(start, end))));
        }
        return List.copyOf(vectors);
    }

    private List<float[]> embedBatch(List<String> batch) {
        long startedAt = System.nanoTime();
        int attempts = 0;
        while (true) {
            attempts++;
            try {
                EmbeddingResponse response = embeddingModel.embedForResponse(batch);
                List<float[]> vectors = validateResponse(response, batch.size());
                int inputTokens = inputTokens(response);
                observability.record(successObservation(batch.size(), inputTokens, startedAt, attempts));
                return vectors;
            } catch (RuntimeException exception) {
                if (attempts >= properties.getMaximumAttempts() || exception instanceof IllegalArgumentException) {
                    observability.record(failureObservation(batch.size(), startedAt, attempts, exception));
                    throw new IllegalStateException(
                            "向量模型调用失败，已尝试 " + attempts + " 次，模型=" + properties.getModelName(),
                            exception
                    );
                }
                try {
                    sleepBeforeRetry(attempts);
                } catch (IllegalStateException interruptedException) {
                    observability.record(failureObservation(
                            batch.size(), startedAt, attempts, interruptedException));
                    throw interruptedException;
                }
            }
        }
    }

    private List<float[]> validateResponse(EmbeddingResponse response, int expectedCount) {
        if (response == null || response.getResults() == null || response.getResults().size() != expectedCount) {
            throw new IllegalArgumentException("向量模型返回数量与输入数量不一致");
        }
        List<float[]> vectors = new ArrayList<>(expectedCount);
        for (Embedding result : response.getResults()) {
            float[] vector = result == null ? null : result.getOutput();
            if (vector == null || vector.length != properties.getDimensions()) {
                int actual = vector == null ? 0 : vector.length;
                throw new IllegalArgumentException(
                        "向量模型返回维度必须为 " + properties.getDimensions() + "，实际为 " + actual);
            }
            vectors.add(vector.clone());
        }
        return List.copyOf(vectors);
    }

    private int inputTokens(EmbeddingResponse response) {
        if (response.getMetadata() == null) {
            return 0;
        }
        Usage usage = response.getMetadata().getUsage();
        return usage == null || usage.getPromptTokens() == null ? 0 : Math.max(0, usage.getPromptTokens());
    }

    private EmbeddingCallObservation successObservation(
            int inputCount,
            int inputTokens,
            long startedAt,
            int attempts
    ) {
        return new EmbeddingCallObservation(
                properties.getModelName(), inputCount, inputTokens, estimatedCost(inputTokens),
                elapsedMillis(startedAt), attempts, true, null
        );
    }

    private EmbeddingCallObservation failureObservation(
            int inputCount,
            long startedAt,
            int attempts,
            RuntimeException exception
    ) {
        return new EmbeddingCallObservation(
                properties.getModelName(), inputCount, 0, BigDecimal.ZERO,
                elapsedMillis(startedAt), attempts, false, exception.getClass().getSimpleName()
        );
    }

    private BigDecimal estimatedCost(int inputTokens) {
        return properties.getInputCostPerMillionTokens()
                .multiply(BigDecimal.valueOf(inputTokens))
                .divide(ONE_MILLION, 10, RoundingMode.HALF_UP)
                .stripTrailingZeros();
    }

    private void sleepBeforeRetry(int failedAttempts) {
        Duration delay = properties.getRetryInitialBackoff().multipliedBy(1L << Math.min(failedAttempts - 1, 20));
        if (delay.isZero() || delay.isNegative()) {
            return;
        }
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("向量模型重试等待被中断", exception);
        }
    }

    private long elapsedMillis(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000L);
    }
}
