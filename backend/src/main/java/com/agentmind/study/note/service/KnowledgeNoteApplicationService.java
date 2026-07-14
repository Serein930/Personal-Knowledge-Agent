package com.agentmind.study.note.service;

import com.agentmind.agent.service.AgentToolExecutionAuthorizer;
import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.common.response.PageResponse;
import com.agentmind.study.note.model.KnowledgeNote;
import com.agentmind.study.note.model.dto.KnowledgeNoteResponse;
import com.agentmind.study.note.repository.KnowledgeNoteRepository;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;

/**
 * 知识笔记应用服务。
 *
 * <p>创建动作由已确认的写工具调用；查询动作仍会校验当前演示用户和知识空间边界。</p>
 */
@Service
public class KnowledgeNoteApplicationService {

    private final KnowledgeNoteRepository noteRepository;
    private final AgentToolExecutionAuthorizer authorizer;

    public KnowledgeNoteApplicationService(
            KnowledgeNoteRepository noteRepository,
            AgentToolExecutionAuthorizer authorizer
    ) {
        this.noteRepository = noteRepository;
        this.authorizer = authorizer;
    }

    public KnowledgeNoteResponse createFromAgent(
            AgentToolExecutionContext context,
            String title,
            String content
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        KnowledgeNote saved = noteRepository.save(new KnowledgeNote(
                null,
                context.ownerUserId(),
                context.workspaceId(),
                context.conversationId(),
                context.requestId(),
                title,
                content,
                now,
                now
        ));
        return toResponse(saved);
    }

    public PageResponse<KnowledgeNoteResponse> list(
            AgentToolExecutionContext context,
            int page,
            int pageSize
    ) {
        authorizer.authorize(context);
        int offset = (page - 1) * pageSize;
        return new PageResponse<>(
                noteRepository.findByOwnerUserIdAndWorkspaceId(
                                context.ownerUserId(), context.workspaceId(), offset, pageSize
                        ).stream()
                        .map(this::toResponse)
                        .toList(),
                page,
                pageSize,
                noteRepository.countByOwnerUserIdAndWorkspaceId(context.ownerUserId(), context.workspaceId())
        );
    }

    private KnowledgeNoteResponse toResponse(KnowledgeNote note) {
        return new KnowledgeNoteResponse(
                note.id(), note.workspaceId(), note.sourceConversationId(), note.requestId(),
                note.title(), note.content(),
                note.createdAt(), note.updatedAt()
        );
    }
}
