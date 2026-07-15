package com.agentmind.study.session.service;

import com.agentmind.agent.service.AgentToolExecutionAuthorizer;
import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.common.exception.BusinessException;
import com.agentmind.common.exception.ErrorCode;
import com.agentmind.common.response.PageResponse;
import com.agentmind.study.flashcard.model.StudyFlashcard;
import com.agentmind.study.flashcard.model.dto.SubmitFlashcardReviewRequest;
import com.agentmind.study.flashcard.model.dto.SubmittedFlashcardReviewResponse;
import com.agentmind.study.flashcard.repository.StudyFlashcardRepository;
import com.agentmind.study.flashcard.service.FlashcardReviewTransactionBoundary;
import com.agentmind.study.flashcard.service.StudyFlashcardResponseMapper;
import com.agentmind.study.flashcard.service.StudyFlashcardReviewApplicationService;
import com.agentmind.study.session.model.StudyReviewSession;
import com.agentmind.study.session.model.StudyReviewSessionItem;
import com.agentmind.study.session.model.StudyReviewSessionItemStatus;
import com.agentmind.study.session.model.StudyReviewSessionStatus;
import com.agentmind.study.session.model.dto.CreateReviewSessionRequest;
import com.agentmind.study.session.model.dto.StudyReviewSessionResponse;
import com.agentmind.study.session.model.dto.SubmittedSessionReviewResponse;
import com.agentmind.study.session.repository.StudyReviewSessionRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;

/**
 * 复习会话应用服务。
 *
 * <p>服务在创建时冻结到期卡片队列；会话内评分复用已有评分服务，因此幂等、算法选择、事务和空间隔离
 * 不会形成第二套实现。数据库模式下评分事实与队列推进加入同一事务。</p>
 */
@Service
public class StudyReviewSessionApplicationService {

    private final StudyReviewSessionRepository sessionRepository;
    private final StudyFlashcardRepository flashcardRepository;
    private final StudyFlashcardReviewApplicationService reviewService;
    private final FlashcardReviewTransactionBoundary transactionBoundary;
    private final AgentToolExecutionAuthorizer authorizer;
    private final StudyFlashcardResponseMapper responseMapper;

    public StudyReviewSessionApplicationService(
            StudyReviewSessionRepository sessionRepository,
            StudyFlashcardRepository flashcardRepository,
            StudyFlashcardReviewApplicationService reviewService,
            FlashcardReviewTransactionBoundary transactionBoundary,
            AgentToolExecutionAuthorizer authorizer,
            StudyFlashcardResponseMapper responseMapper
    ) {
        this.sessionRepository = sessionRepository;
        this.flashcardRepository = flashcardRepository;
        this.reviewService = reviewService;
        this.transactionBoundary = transactionBoundary;
        this.authorizer = authorizer;
        this.responseMapper = responseMapper;
    }

    public StudyReviewSessionResponse create(
            AgentToolExecutionContext context,
            CreateReviewSessionRequest request
    ) {
        authorizer.authorize(context);
        OffsetDateTime now = OffsetDateTime.now();
        List<StudyFlashcard> dueCards = flashcardRepository.findDueByOwnerUserIdAndWorkspaceId(
                context.ownerUserId(), context.workspaceId(), now, 0, request.limit()
        );
        if (dueCards.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "当前知识空间没有到期卡片");
        }
        StudyReviewSession session = new StudyReviewSession(
                null, context.ownerUserId(), context.workspaceId(), StudyReviewSessionStatus.IN_PROGRESS,
                dueCards.size(), 0, 0, now, null, null, null, now, now
        );
        List<StudyReviewSessionItem> items = IntStream.range(0, dueCards.size())
                .mapToObj(index -> new StudyReviewSessionItem(
                        null, context.ownerUserId(), context.workspaceId(), null, dueCards.get(index).id(),
                        index + 1, StudyReviewSessionItemStatus.PENDING, null, null, now
                ))
                .toList();
        StudyReviewSession stored = transactionBoundary.execute(() -> sessionRepository.create(session, items));
        return mapResponse(stored);
    }

    public StudyReviewSessionResponse get(AgentToolExecutionContext context, Long sessionId) {
        authorizer.authorize(context);
        return mapResponse(requireSession(context, sessionId));
    }

    public PageResponse<StudyReviewSessionResponse> list(
            AgentToolExecutionContext context,
            int page,
            int pageSize
    ) {
        authorizer.authorize(context);
        int offset = (page - 1) * pageSize;
        return new PageResponse<>(
                sessionRepository.findByScope(
                                context.ownerUserId(), context.workspaceId(), offset, pageSize
                        ).stream()
                        .map(this::mapResponse)
                        .toList(),
                page,
                pageSize,
                sessionRepository.countByScope(context.ownerUserId(), context.workspaceId())
        );
    }

    public StudyReviewSessionResponse pause(AgentToolExecutionContext context, Long sessionId) {
        return transition(
                context, sessionId, StudyReviewSessionStatus.IN_PROGRESS, StudyReviewSessionStatus.PAUSED,
                "只有进行中的复习会话可以暂停"
        );
    }

    public StudyReviewSessionResponse resume(AgentToolExecutionContext context, Long sessionId) {
        return transition(
                context, sessionId, StudyReviewSessionStatus.PAUSED, StudyReviewSessionStatus.IN_PROGRESS,
                "只有暂停的复习会话可以恢复"
        );
    }

    public StudyReviewSessionResponse abandon(AgentToolExecutionContext context, Long sessionId) {
        authorizer.authorize(context);
        StudyReviewSession current = requireSession(context, sessionId);
        if (current.status() != StudyReviewSessionStatus.IN_PROGRESS
                && current.status() != StudyReviewSessionStatus.PAUSED) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "只有未结束的复习会话可以放弃");
        }
        StudyReviewSession updated = sessionRepository.transitionStatus(
                        context.ownerUserId(), context.workspaceId(), sessionId,
                        current.status(), StudyReviewSessionStatus.ABANDONED, OffsetDateTime.now()
                )
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_CONFLICT, "复习会话状态已经变化，请刷新后重试"
                ));
        return mapResponse(updated);
    }

    public SubmittedSessionReviewResponse submitReview(
            AgentToolExecutionContext context,
            Long sessionId,
            Long flashcardId,
            SubmitFlashcardReviewRequest request
    ) {
        authorizer.authorize(context);
        return transactionBoundary.execute(() -> {
            StudyReviewSession session = requireSession(context, sessionId);
            StudyReviewSessionItem item = requireQueueItem(context, sessionId, flashcardId);
            if (session.status() != StudyReviewSessionStatus.IN_PROGRESS) {
                if (item.status() == StudyReviewSessionItemStatus.REVIEWED) {
                    SubmittedFlashcardReviewResponse reused = reviewService.submit(context, flashcardId, request);
                    return new SubmittedSessionReviewResponse(mapResponse(session), reused);
                }
                throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "当前复习会话未处于可评分状态");
            }
            SubmittedFlashcardReviewResponse review = reviewService.submit(context, flashcardId, request);
            StudyReviewSession updated = sessionRepository.markReviewed(
                    context.ownerUserId(), context.workspaceId(), sessionId, flashcardId,
                    request.score(), review.review().reviewedAt()
            );
            return new SubmittedSessionReviewResponse(mapResponse(updated), review);
        });
    }

    private StudyReviewSessionResponse transition(
            AgentToolExecutionContext context,
            Long sessionId,
            StudyReviewSessionStatus expectedStatus,
            StudyReviewSessionStatus nextStatus,
            String invalidMessage
    ) {
        authorizer.authorize(context);
        StudyReviewSession current = requireSession(context, sessionId);
        if (current.status() != expectedStatus) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, invalidMessage);
        }
        StudyReviewSession updated = sessionRepository.transitionStatus(
                        context.ownerUserId(), context.workspaceId(), sessionId,
                        expectedStatus, nextStatus, OffsetDateTime.now()
                )
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_CONFLICT, "复习会话状态已经变化，请刷新后重试"
                ));
        return mapResponse(updated);
    }

    private StudyReviewSession requireSession(AgentToolExecutionContext context, Long sessionId) {
        return sessionRepository.findByScopeAndId(context.ownerUserId(), context.workspaceId(), sessionId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "复习会话不存在或无权访问"
                ));
    }

    private StudyReviewSessionItem requireQueueItem(
            AgentToolExecutionContext context,
            Long sessionId,
            Long flashcardId
    ) {
        return sessionRepository.findItemsByScopeAndSessionId(
                        context.ownerUserId(), context.workspaceId(), sessionId
                ).stream()
                .filter(item -> flashcardId.equals(item.flashcardId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_CONFLICT,
                        "该卡片不属于当前复习会话"
                ));
    }

    private StudyReviewSessionResponse mapResponse(StudyReviewSession session) {
        List<StudyReviewSessionItem> items = sessionRepository.findItemsByScopeAndSessionId(
                session.ownerUserId(), session.workspaceId(), session.id()
        );
        Map<Long, StudyFlashcard> cards = flashcardRepository.findAllByOwnerUserIdAndWorkspaceId(
                        session.ownerUserId(), session.workspaceId()
                ).stream()
                .collect(Collectors.toMap(StudyFlashcard::id, Function.identity()));
        List<StudyReviewSessionResponse.QueueItem> queue = items.stream()
                .map(item -> {
                    StudyFlashcard card = cards.get(item.flashcardId());
                    if (card == null) {
                        throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "会话中的复习卡片已不存在");
                    }
                    return new StudyReviewSessionResponse.QueueItem(
                            item.id(), item.position(), item.status(), item.score(), item.reviewedAt(),
                            responseMapper.toFlashcardResponse(card)
                    );
                })
                .toList();
        double progress = session.totalCards() == 0
                ? 100
                : Math.round(session.reviewedCards() * 10000.0 / session.totalCards()) / 100.0;
        return new StudyReviewSessionResponse(
                session.id(), session.workspaceId(), session.status(), session.totalCards(),
                session.reviewedCards(), session.correctCards(), progress, queue,
                session.startedAt(), session.pausedAt(), session.completedAt(),
                session.abandonedAt(), session.updatedAt()
        );
    }
}
