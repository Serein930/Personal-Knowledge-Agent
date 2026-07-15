package com.agentmind.study.flashcard.service;

import com.agentmind.agent.service.AgentToolExecutionAuthorizer;
import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.common.response.PageResponse;
import com.agentmind.common.exception.BusinessException;
import com.agentmind.common.exception.ErrorCode;
import com.agentmind.study.flashcard.model.StudyFlashcard;
import com.agentmind.study.flashcard.model.StudyFlashcardStatus;
import com.agentmind.study.flashcard.model.dto.ManageFlashcardRequest;
import com.agentmind.study.flashcard.model.dto.RescheduleFlashcardRequest;
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

    public StudyFlashcardResponse suspend(
            AgentToolExecutionContext context,
            Long flashcardId,
            ManageFlashcardRequest request
    ) {
        return manageStatus(context, flashcardId, request.expectedVersion(), StudyFlashcardStatus.SUSPENDED);
    }

    public StudyFlashcardResponse resume(
            AgentToolExecutionContext context,
            Long flashcardId,
            ManageFlashcardRequest request
    ) {
        authorizer.authorize(context);
        StudyFlashcard current = requireFlashcard(context, flashcardId);
        if (current.status() != StudyFlashcardStatus.SUSPENDED) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "只有暂停状态的卡片可以恢复");
        }
        requireExpectedVersion(current, request.expectedVersion());
        StudyFlashcardStatus resumedStatus = resolveResumedStatus(current);
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime resumedDueAt = current.dueAt().isBefore(now) ? now : current.dueAt();
        return updateManaged(current, resumedStatus, resumedDueAt, now);
    }

    public StudyFlashcardResponse reschedule(
            AgentToolExecutionContext context,
            Long flashcardId,
            RescheduleFlashcardRequest request
    ) {
        authorizer.authorize(context);
        StudyFlashcard current = requireFlashcard(context, flashcardId);
        if (current.status() == StudyFlashcardStatus.SUSPENDED) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "暂停状态的卡片需要先恢复再重新排期");
        }
        requireExpectedVersion(current, request.expectedVersion());
        return updateManaged(current, current.status(), request.dueAt(), OffsetDateTime.now());
    }

    private StudyFlashcardResponse manageStatus(
            AgentToolExecutionContext context,
            Long flashcardId,
            long expectedVersion,
            StudyFlashcardStatus nextStatus
    ) {
        authorizer.authorize(context);
        StudyFlashcard current = requireFlashcard(context, flashcardId);
        requireExpectedVersion(current, expectedVersion);
        if (current.status() == nextStatus) {
            return responseMapper.toFlashcardResponse(current);
        }
        return updateManaged(current, nextStatus, current.dueAt(), OffsetDateTime.now());
    }

    private StudyFlashcardResponse updateManaged(
            StudyFlashcard current,
            StudyFlashcardStatus status,
            OffsetDateTime dueAt,
            OffsetDateTime now
    ) {
        StudyFlashcard candidate = current.manage(status, dueAt, now);
        StudyFlashcard updated = flashcardRepository.updateSchedule(candidate, current.version())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_CONFLICT,
                        "卡片已被其他请求修改，请刷新后重试"
                ));
        return responseMapper.toFlashcardResponse(updated);
    }

    private StudyFlashcardStatus resolveResumedStatus(StudyFlashcard card) {
        if (card.lastReviewedAt() == null) {
            return StudyFlashcardStatus.NEW;
        }
        return card.repetitionCount() >= 2 ? StudyFlashcardStatus.REVIEW : StudyFlashcardStatus.LEARNING;
    }

    private void requireExpectedVersion(StudyFlashcard card, long expectedVersion) {
        if (card.version() != expectedVersion) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "卡片版本已变化，请刷新后重试");
        }
    }

    private StudyFlashcard requireFlashcard(AgentToolExecutionContext context, Long flashcardId) {
        return flashcardRepository.findByOwnerUserIdAndWorkspaceIdAndId(
                        context.ownerUserId(), context.workspaceId(), flashcardId
                )
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "复习卡片不存在或无权访问"
                ));
    }
}
