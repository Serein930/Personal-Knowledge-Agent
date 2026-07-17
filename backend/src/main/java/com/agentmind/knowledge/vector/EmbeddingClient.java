package com.agentmind.knowledge.vector;

import java.util.List;

/**
 * 将文本转换为向量的端口。
 *
 * <p>该项目级抽象可以让当前确定性本地实现，在后续替换为真实向量模型时不影响摄取和检索服务。</p>
 */
public interface EmbeddingClient {

    float[] embed(String text);

    /**
     * 批量生成文本向量。
     *
     * <p>默认实现保持现有轻量测试和确定性实现兼容。真实模型适配器应覆盖本方法，
     * 把多个文本合并为受控批次，减少网络往返和模型调用成本。</p>
     */
    default List<float[]> embedAll(List<String> texts) {
        return texts.stream().map(this::embed).toList();
    }
}
