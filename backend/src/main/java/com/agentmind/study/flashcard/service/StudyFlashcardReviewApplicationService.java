package com.agentmind.study.flashcard.service;

import com.agentmind.agent.service.AgentToolExecutionAuthorizer;
import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.common.exception.BusinessException;
import com.agentmind.common.exception.ErrorCode;
import com.agentmind.common.response.PageResponse;
import com.agentmind.study.flashcard.algorithm.SpacedRepetitionAlgorithm;
import com.agentmind.study.flashcard.model.StudyFlashcard;
import com.agentmind.study.flashcard.model.StudyFlashcardReview;
import com.agentmind.study.flashcard.model.StudyFlashcardSchedule;
import com.agentmind.study.flashcard.model.StudyFlashcardStatus;
import com.agentmind.study.flashcard.model.dto.StudyFlashcardReviewResponse;
import com.agentmind.study.flashcard.model.dto.SubmitFlashcardReviewRequest;
import com.agentmind.study.flashcard.model.dto.SubmittedFlashcardReviewResponse;
import com.agentmind.study.flashcard.repository.StudyFlashcardRepository;
import com.agentmind.study.flashcard.repository.StudyFlashcardReviewRepository;
import java.time.OffsetDateTime;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 复习评分应用服务。
 *
 * <p>服务负责权限隔离、请求幂等、算法调用、乐观并发重试和事务编排。算法不直接访问仓储，Controller
 * 也不参与调度计算，从而保证未来替换 FSRS 时接口契约和数据访问边界保持稳定。</p>
 */
@Service
public class StudyFlashcardReviewApplicationService {

    private static final int MAX_CONCURRENT_RETRIES = 3;

    private final StudyFlashcardRepository flashcardRepository;
    private final StudyFlashcardReviewRepository reviewRepository;
    private final SpacedRepetitionAlgorithm algorithm;
    private final FlashcardReviewTransactionBoundary transactionBoundary;
    private final AgentToolExecutionAuthorizer authorizer;
    private final StudyFlashcardResponseMapper responseMapper;

    public StudyFlashcardReviewApplicationService(
            StudyFlashcardRepository flashcardRepository,
            StudyFlashcardReviewRepository reviewRepository,
            SpacedRepetitionAlgorithm algorithm,
            FlashcardReviewTransactionBoundary transactionBoundary,
            AgentToolExecutionAuthorizer authorizer,
            StudyFlashcardResponseMapper responseMapper
    ) {
        this.flashcardRepository = flashcardRepository;
        this.reviewRepository = reviewRepository;
        this.algorithm = algorithm;
        this.transactionBoundary = transactionBoundary;
        this.authorizer = authorizer;
        this.responseMapper = responseMapper;
    }

    public SubmittedFlashcardReviewResponse submit(
            AgentToolExecutionContext context,
            Long flashcardId,
            SubmitFlashcardReviewRequest request
    ) {
        authorizer.authorize(context);
        String requestId = request.requestId().trim();
        for (int attempt = 1; attempt <= MAX_CONCURRENT_RETRIES; attempt++) {
            try {
                return transactionBoundary.execute(() -> submitOnce(
                        context, flashcardId, requestId, request.score()
                ));
            } catch (ConcurrentFlashcardReviewException exception) {
                if (attempt == MAX_CONCURRENT_RETRIES) {
                    throw new BusinessException(
                            ErrorCode.RESOURCE_CONFLICT,
                            "复习卡片状态正在变化，请稍后重试"
                    );
                }
            } catch (DataIntegrityViolationException exception) {
                // 并发重复请求可能同时通过首次查询，唯一索引会让其中一个事务回滚；回滚后读取成功记录复用。
                return resolveExisting(context, flashcardId, requestId, request.score());
            }
        }
        throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "复习卡片状态更新失败");
    }

    public PageResponse<StudyFlashcardReviewResponse> listReviews(
            AgentToolExecutionContext context,
            Long flashcardId,
            int page,
            int pageSize
    ) {
        authorizer.authorize(context);
        requireFlashcard(context, flashcardId);
        int offset = (page - 1) * pageSize;
        return new PageResponse<>(
                reviewRepository.findByOwnerUserIdAndWorkspaceIdAndFlashcardId(
                                context.ownerUserId(), context.workspaceId(), flashcardId, offset, pageSize
                        ).stream()
                        .map(responseMapper::toReviewResponse)
                        .toList(),
                page,
                pageSize,
                reviewRepository.countByOwnerUserIdAndWorkspaceIdAndFlashcardId(
                        context.ownerUserId(), context.workspaceId(), flashcardId
                )
        );
    }

    private SubmittedFlashcardReviewResponse submitOnce(
            AgentToolExecutionContext context,
            Long flashcardId,
            String requestId,
            int score
    ) {
        StudyFlashcardReview existing = reviewRepository
                .findByOwnerUserIdAndWorkspaceIdAndRequestId(
                        context.ownerUserId(), context.workspaceId(), requestId
                )
                .orElse(null);
        if (existing != null) {
            return toReusedResponse(context, flashcardId, score, existing);
        }

        StudyFlashcard current = requireFlashcard(context, flashcardId);
        if (current.status() == StudyFlashcardStatus.SUSPENDED) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "暂停状态的复习卡片不能提交评分");
        }
        OffsetDateTime reviewedAt = OffsetDateTime.now();
        StudyFlashcardSchedule nextSchedule = algorithm.calculate(current.schedule(), score, reviewedAt);
        StudyFlashcard candidate = current.applySchedule(nextSchedule, reviewedAt);
        StudyFlashcard updated = flashcardRepository.updateSchedule(candidate, current.version())
                .orElseThrow(ConcurrentFlashcardReviewException::new);
        StudyFlashcardReview savedReview = reviewRepository.save(createReview(
                current, updated, requestId, score, reviewedAt
        ));
        return new SubmittedFlashcardReviewResponse(
                responseMapper.toFlashcardResponse(updated),
                responseMapper.toReviewResponse(savedReview),
                false
        );
    }

    private SubmittedFlashcardReviewResponse resolveExisting(
            AgentToolExecutionContext context,
            Long flashcardId,
            String requestId,
            int score
    ) {
        StudyFlashcardReview existing = reviewRepository
                .findByOwnerUserIdAndWorkspaceIdAndRequestId(
                        context.ownerUserId(), context.workspaceId(), requestId
                )
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_CONFLICT,
                        "复习请求发生并发冲突，请重新查询后重试"
                ));
        return toReusedResponse(context, flashcardId, score, existing);
    }

    private SubmittedFlashcardReviewResponse toReusedResponse(
            AgentToolExecutionContext context,
            Long flashcardId,
            int score,
            StudyFlashcardReview existing
    ) {
        if (!flashcardId.equals(existing.flashcardId()) || score != existing.score()) {
            throw new BusinessException(
                    ErrorCode.RESOURCE_CONFLICT,
                    "相同复习请求编号不能用于不同卡片或评分"
            );
        }
        StudyFlashcard current = requireFlashcard(context, flashcardId);
        return new SubmittedFlashcardReviewResponse(
                responseMapper.toFlashcardResponse(current),
                responseMapper.toReviewResponse(existing),
                true
        );
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

    private StudyFlashcardReview createReview(
            StudyFlashcard previous,
            StudyFlashcard next,
            String requestId,
            int score,
            OffsetDateTime reviewedAt
    ) {
        return new StudyFlashcardReview(
                null,
                previous.ownerUserId(),
                previous.workspaceId(),
                previous.id(),
                requestId,
                score,
                previous.status(),
                next.status(),
                previous.intervalDays(),
                next.intervalDays(),
                previous.easeFactor(),
                next.easeFactor(),
                previous.dueAt(),
                next.dueAt(),
                algorithm.name(),
                reviewedAt,
                reviewedAt
        );
    }

    /**
     * 内部并发信号只用于触发事务级重试，不直接暴露为接口错误。
     */
    private static final class ConcurrentFlashcardReviewException extends RuntimeException {
    }
}
