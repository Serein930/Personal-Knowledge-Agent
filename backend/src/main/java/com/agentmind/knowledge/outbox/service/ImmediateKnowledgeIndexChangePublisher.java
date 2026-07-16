package com.agentmind.knowledge.outbox.service;

import com.agentmind.document.chunk.DocumentChunk;
import com.agentmind.knowledge.keyword.KeywordIndex;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Outbox 未开启时使用的同步发布器，主要服务于内存开发模式。 */
@Component
@ConditionalOnProperty(prefix = "agentmind.knowledge-index.outbox", name = "enabled", havingValue = "false", matchIfMissing = true)
public class ImmediateKnowledgeIndexChangePublisher implements KnowledgeIndexChangePublisher {

    private final KeywordIndex keywordIndex;

    public ImmediateKnowledgeIndexChangePublisher(KeywordIndex keywordIndex) {
        this.keywordIndex = keywordIndex;
    }

    @Override
    public void publishUpsert(Long workspaceId, Long documentId, List<DocumentChunk> chunks) {
        keywordIndex.replaceDocumentChunks(workspaceId, documentId, chunks);
    }

    @Override
    public void publishDelete(Long workspaceId, Long documentId) {
        keywordIndex.deleteDocumentChunks(workspaceId, documentId);
    }
}
