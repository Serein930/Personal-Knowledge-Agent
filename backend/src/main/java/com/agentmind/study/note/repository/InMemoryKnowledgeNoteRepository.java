package com.agentmind.study.note.repository;

import com.agentmind.study.note.model.KnowledgeNote;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * 知识笔记内存存储适配器。
 *
 * <p>该适配器只用于 Stage 7 本地联调。正式数据库表落地后，需要保留用户和知识空间联合过滤条件。</p>
 */
@Repository
@ConditionalOnProperty(
        prefix = "agentmind.agent.persistence",
        name = "store",
        havingValue = "memory",
        matchIfMissing = true
)
public class InMemoryKnowledgeNoteRepository implements KnowledgeNoteRepository {

    private final AtomicLong idGenerator = new AtomicLong(1);
    private final ConcurrentHashMap<Long, KnowledgeNote> notes = new ConcurrentHashMap<>();

    @Override
    public synchronized KnowledgeNote save(KnowledgeNote note) {
        KnowledgeNote existing = notes.values().stream()
                .filter(candidate -> candidate.ownerUserId().equals(note.ownerUserId()))
                .filter(candidate -> candidate.workspaceId().equals(note.workspaceId()))
                .filter(candidate -> candidate.requestId().equals(note.requestId()))
                .findFirst()
                .orElse(null);
        if (existing != null) {
            return existing;
        }
        KnowledgeNote stored = note.id() == null
                ? new KnowledgeNote(
                        idGenerator.getAndIncrement(), note.ownerUserId(), note.workspaceId(),
                        note.sourceConversationId(), note.requestId(), note.title(), note.content(),
                        note.createdAt(), note.updatedAt()
                )
                : note;
        notes.put(stored.id(), stored);
        return stored;
    }

    @Override
    public List<KnowledgeNote> findByOwnerUserIdAndWorkspaceId(
            Long ownerUserId,
            Long workspaceId,
            int offset,
            int limit
    ) {
        return notes.values().stream()
                .filter(note -> ownerUserId.equals(note.ownerUserId()))
                .filter(note -> workspaceId.equals(note.workspaceId()))
                .sorted(Comparator.comparing(KnowledgeNote::createdAt)
                        .reversed()
                        .thenComparing(KnowledgeNote::id, Comparator.reverseOrder()))
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    public long countByOwnerUserIdAndWorkspaceId(Long ownerUserId, Long workspaceId) {
        return notes.values().stream()
                .filter(note -> ownerUserId.equals(note.ownerUserId()))
                .filter(note -> workspaceId.equals(note.workspaceId()))
                .count();
    }
}
