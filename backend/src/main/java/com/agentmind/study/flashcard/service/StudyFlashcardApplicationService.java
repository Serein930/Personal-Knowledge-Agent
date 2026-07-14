package com.agentmind.study.flashcard.service;

import com.agentmind.agent.service.AgentToolExecutionAuthorizer;
import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.common.response.PageResponse;
import com.agentmind.study.flashcard.model.StudyFlashcard;
import com.agentmind.study.flashcard.model.StudyFlashcardStatus;
import com.agentmind.study.flashcard.model.dto.StudyFlashcardResponse;
import com.agentmind.study.flashcard.repository.StudyFlashcardRepository;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;

/**
 * 复习卡片应用服务。
 */
@Service
public class StudyFlashcardApplicationService {

    private final StudyFlashcardRepository flashcardRepository;
    private final AgentToolExecutionAuthorizer authorizer;
    private final StudyFlashcardResponseMapper responseMapper;

    public StudyFlashcardApplicationService(
            StudyFlashcardRepository flashcardRepository,
            AgentToolExecutionAuthorizer authorizer,
            StudyFlashcardResponseMapper responseMapper
    ) {
        this.flashcardRepository = flashcardRepository;
        this.authorizer = authorizer;
        this.responseMapper = responseMapper;
    }

    public StudyFlashcardResponse createFromAgent(
            AgentToolExecutionContext context,
            String question,
            String answer,
            String explanation
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        StudyFlashcard saved = flashcardRepository.save(new StudyFlashcard(
                null, context.ownerUserId(), context.workspaceId(), context.conversationId(), context.requestId(),
                question, answer, explanation,
                StudyFlashcardStatus.NEW, 0, 0, 2.5, 0, now, null, 0,
                now, now
        ));
        return responseMapper.toFlashcardResponse(saved);
    }

    public PageResponse<StudyFlashcardResponse> list(
            AgentToolExecutionContext context,
            int page,
            int pageSize
    ) {
        authorizer.authorize(context);
        int offset = (page - 1) * pageSize;
        return new PageResponse<>(
                flashcardRepository.findByOwnerUserIdAndWorkspaceId(
                                context.ownerUserId(), context.workspaceId(), offset, pageSize
                        ).stream()
                        .map(responseMapper::toFlashcardResponse)
                        .toList(),
                page,
                pageSize,
                flashcardRepository.countByOwnerUserIdAndWorkspaceId(
                        context.ownerUserId(), context.workspaceId()
                )
        );
    }

    public PageResponse<StudyFlashcardResponse> listDue(
            AgentToolExecutionContext context,
            int page,
            int pageSize
    ) {
        authorizer.authorize(context);
        OffsetDateTime dueBefore = OffsetDateTime.now();
        int offset = (page - 1) * pageSize;
        return new PageResponse<>(
                flashcardRepository.findDueByOwnerUserIdAndWorkspaceId(
                                context.ownerUserId(), context.workspaceId(), dueBefore, offset, pageSize
                        ).stream()
                        .map(responseMapper::toFlashcardResponse)
                        .toList(),
                page,
                pageSize,
                flashcardRepository.countDueByOwnerUserIdAndWorkspaceId(
                        context.ownerUserId(), context.workspaceId(), dueBefore
                )
        );
    }
}
