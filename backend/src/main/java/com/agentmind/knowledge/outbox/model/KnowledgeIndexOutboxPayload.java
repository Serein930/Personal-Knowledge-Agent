package com.agentmind.knowledge.outbox.model;

import com.agentmind.document.chunk.DocumentChunk;
import java.util.List;

/**
 * 索引事件不可变载荷。
 *
 * <p>完整保存当次切分结果，避免异步消费时读到文档的新版本或依赖尚未持久化的内存文档仓储。</p>
 */
public record KnowledgeIndexOutboxPayload(List<DocumentChunk> chunks) {

    public KnowledgeIndexOutboxPayload {
        chunks = chunks == null ? List.of() : List.copyOf(chunks);
    }
}
