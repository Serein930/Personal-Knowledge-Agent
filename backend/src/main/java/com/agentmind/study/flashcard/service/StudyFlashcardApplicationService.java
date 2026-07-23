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
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 复习卡片应用服务。
 */
@Service
public class StudyFlashcardApplicationService {

    private static final int MAX_QUESTION_LENGTH = 240;
    private static final int MAX_ANSWER_LENGTH = 800;
    private static final List<String> FORBIDDEN_FAILURE_TEXTS = List.of(
            "模型调用失败", "流式调用失败", "降级模式", "请稍后重试", "回答生成失败"
    );

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
        return createFromAgent(context, question, answer, explanation, null, null);
    }

    /** 创建带知识来源的卡片，为后续按主题和文档生成学习任务保留结构化关联。 */
    public StudyFlashcardResponse createFromAgent(
            AgentToolExecutionContext context,
            String question,
            String answer,
            String explanation,
            Long sourceDocumentId,
            String topic
    ) {
        requireValidCardContent(question, answer);
        OffsetDateTime now = OffsetDateTime.now();
        StudyFlashcard saved = flashcardRepository.save(new StudyFlashcard(
                null, context.ownerUserId(), context.workspaceId(), context.conversationId(),
                sourceDocumentId, normalizeTopic(topic), context.requestId(),
                question, answer, explanation,
                StudyFlashcardStatus.NEW, 0, 0, 2.5, 0, now, null, 0,
                now, now
        ));
        return responseMapper.toFlashcardResponse(saved);
    }

    /**
     * 所有卡片写入口统一执行质量底线，防止模型异常提示、空答案或整段超长内容进入复习队列。
     */
    private void requireValidCardContent(String question, String answer) {
        if (!StringUtils.hasText(question) || !StringUtils.hasText(answer)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "复习卡片的问题和答案不能为空");
        }
        if (question.trim().length() > MAX_QUESTION_LENGTH) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "复习卡片问题过长，请只保留一个具体知识点");
        }
        if (answer.trim().length() > MAX_ANSWER_LENGTH) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "复习卡片答案过长，请拆分为多张单知识点卡片");
        }
        String combinedText = question + " " + answer;
        if (FORBIDDEN_FAILURE_TEXTS.stream().anyMatch(combinedText::contains)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "模型失败提示不能保存为复习卡片");
        }
    }

    private String normalizeTopic(String topic) {
        if (topic == null || topic.isBlank()) {
            return null;
        }
        String normalized = topic.trim();
        return normalized.length() <= 100 ? normalized : normalized.substring(0, 100);
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

    /** 用户显式删除卡片，不经过智能体写工具确认。 */
    public void delete(AgentToolExecutionContext context, Long flashcardId) {
        authorizer.authorize(context);
        requireFlashcard(context, flashcardId);
        if (!flashcardRepository.deleteByOwnerUserIdAndWorkspaceIdAndId(
                context.ownerUserId(), context.workspaceId(), flashcardId)) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "卡片状态已经变化，请刷新后重试");
        }
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
