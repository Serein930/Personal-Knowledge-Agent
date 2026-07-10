package com.agentmind.knowledge.vector;

/**
 * 将文本转换为向量的端口。
 *
 * <p>该项目级抽象可以让当前确定性本地实现，在后续替换为真实向量模型时不影响摄取和检索服务。</p>
 */
public interface EmbeddingClient {

    float[] embed(String text);
}
