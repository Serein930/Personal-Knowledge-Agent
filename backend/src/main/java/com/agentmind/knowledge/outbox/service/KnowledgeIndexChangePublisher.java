package com.agentmind.knowledge.outbox.service;

import com.agentmind.document.chunk.DocumentChunk;
import java.util.List;

/**
 * 关键词索引变更发布端口。
 *
 * <p>开发模式可以直接写入关键词索引，生产模式则先写入事务消息，
 * 业务服务无需感知两种交付方式的差异。</p>
 */
public interface KnowledgeIndexChangePublisher {

    void publishUpsert(Long workspaceId, Long documentId, List<DocumentChunk> chunks);

    void publishDelete(Long workspaceId, Long documentId);
}
