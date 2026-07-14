package com.agentmind.study.note.repository;

import com.agentmind.study.note.model.KnowledgeNote;
import java.util.List;

/**
 * 知识笔记存储端口。
 */
public interface KnowledgeNoteRepository {

    KnowledgeNote save(KnowledgeNote note);

    List<KnowledgeNote> findByOwnerUserIdAndWorkspaceId(
            Long ownerUserId,
            Long workspaceId,
            int offset,
            int limit
    );

    long countByOwnerUserIdAndWorkspaceId(Long ownerUserId, Long workspaceId);
}
