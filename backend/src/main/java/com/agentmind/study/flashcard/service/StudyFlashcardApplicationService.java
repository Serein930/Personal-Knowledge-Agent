package com.agentmind.study.flashcard.service;

import com.agentmind.agent.service.AgentToolExecutionAuthorizer;
import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.common.response.PageResponse;
import com.agentmind.study.flashcard.model.StudyFlashcard;
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

    public StudyFlashcardApplicationService(
            StudyFlashcardRepository flashcardRepository,
            AgentToolExecutionAuthorizer authorizer
    ) {
        this.flashcardRepository = flashcardRepository;
        this.authorizer = authorizer;
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
                question, answer, explanation, now, now
        ));
        return toResponse(saved);
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
                        .map(this::toResponse)
                        .toList(),
                page,
                pageSize,
                flashcardRepository.countByOwnerUserIdAndWorkspaceId(
                        context.ownerUserId(), context.workspaceId()
                )
        );
    }

    private StudyFlashcardResponse toResponse(StudyFlashcard flashcard) {
        return new StudyFlashcardResponse(
                flashcard.id(), flashcard.workspaceId(), flashcard.sourceConversationId(), flashcard.requestId(),
                flashcard.question(), flashcard.answer(), flashcard.explanation(),
                flashcard.createdAt(), flashcard.updatedAt()
        );
    }
}
