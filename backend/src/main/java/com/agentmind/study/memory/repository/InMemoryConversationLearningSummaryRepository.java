package com.agentmind.study.memory.repository;

import com.agentmind.study.memory.model.ConversationLearningSummary;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/** 长期会话摘要内存适配器。 */
@Repository
@ConditionalOnProperty(prefix = "agentmind.agent.persistence", name = "store", havingValue = "memory", matchIfMissing = true)
public class InMemoryConversationLearningSummaryRepository implements ConversationLearningSummaryRepository {

    private final AtomicLong idGenerator = new AtomicLong(1);
    private final ConcurrentHashMap<String, ConversationLearningSummary> summaries = new ConcurrentHashMap<>();

    @Override
    public ConversationLearningSummary saveOrUpdate(ConversationLearningSummary summary) {
        return summaries.compute(key(summary.ownerUserId(), summary.workspaceId(), summary.conversationId()),
                (ignored, current) -> new ConversationLearningSummary(
                        current == null ? idGenerator.getAndIncrement() : current.id(),
                        summary.ownerUserId(), summary.workspaceId(), summary.conversationId(), summary.summary(),
                        summary.topics(), summary.weakTopics(), summary.messageCount(),
                        current == null ? 0 : current.version() + 1,
                        current == null ? summary.createdAt() : current.createdAt(), summary.updatedAt()
                ));
    }

    @Override
    public List<ConversationLearningSummary> findByScope(Long ownerUserId, Long workspaceId, int limit) {
        return summaries.values().stream()
                .filter(summary -> ownerUserId.equals(summary.ownerUserId()))
                .filter(summary -> workspaceId.equals(summary.workspaceId()))
                .sorted(Comparator.comparing(ConversationLearningSummary::updatedAt).reversed()
                        .thenComparing(ConversationLearningSummary::id, Comparator.reverseOrder()))
                .limit(limit).toList();
    }

    private String key(Long ownerUserId, Long workspaceId, Long conversationId) {
        return ownerUserId + ":" + workspaceId + ":" + conversationId;
    }
}
