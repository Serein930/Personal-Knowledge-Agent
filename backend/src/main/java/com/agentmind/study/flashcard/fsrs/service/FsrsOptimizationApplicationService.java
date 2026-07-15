package com.agentmind.study.flashcard.fsrs.service;

import com.agentmind.agent.service.AgentToolExecutionAuthorizer;
import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.common.response.PageResponse;
import com.agentmind.study.flashcard.fsrs.model.FsrsOptimizationJob;
import com.agentmind.study.flashcard.fsrs.model.FsrsOptimizationJobStatus;
import com.agentmind.study.flashcard.fsrs.model.FsrsUserProfile;
import com.agentmind.study.flashcard.fsrs.model.dto.FsrsOptimizationJobResponse;
import com.agentmind.study.flashcard.fsrs.model.dto.StartFsrsOptimizationRequest;
import com.agentmind.study.flashcard.fsrs.optimization.FsrsOptimizationResult;
import com.agentmind.study.flashcard.fsrs.optimization.FsrsParameterOptimizer;
import com.agentmind.study.flashcard.fsrs.repository.FsrsOptimizationJobRepository;
import com.agentmind.study.flashcard.model.StudyFlashcardReview;
import com.agentmind.study.flashcard.repository.StudyFlashcardReviewRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * FSRS 历史权重拟合任务服务。
 *
 * <p>服务负责样本门槛、任务状态和版本应用；具体损失函数及搜索算法由优化器端口负责。只有训练集
 * 和验证集都满足接受条件时结果才可应用，避免“训练损失下降”被误判为可以上线。</p>
 */
@Service
public class FsrsOptimizationApplicationService {

    private static final int MINIMUM_REVIEW_COUNT = 50;

    private final FsrsOptimizationJobRepository jobRepository;
    private final StudyFlashcardReviewRepository reviewRepository;
    private final FsrsUserProfileService profileService;
    private final FsrsParameterOptimizer parameterOptimizer;
    private final AgentToolExecutionAuthorizer authorizer;

    public FsrsOptimizationApplicationService(
            FsrsOptimizationJobRepository jobRepository,
            StudyFlashcardReviewRepository reviewRepository,
            FsrsUserProfileService profileService,
            FsrsParameterOptimizer parameterOptimizer,
            AgentToolExecutionAuthorizer authorizer
    ) {
        this.jobRepository = jobRepository;
        this.reviewRepository = reviewRepository;
        this.profileService = profileService;
        this.parameterOptimizer = parameterOptimizer;
        this.authorizer = authorizer;
    }

    public FsrsOptimizationJobResponse start(
            AgentToolExecutionContext context,
            StartFsrsOptimizationRequest request
    ) {
        authorizer.authorize(context);
        return startInternal(context.ownerUserId(), request);
    }

    /**
     * 后台维护任务使用的可信内部入口。
     *
     * <p>用户范围必须来自仓储扫描结果，不能接收外部请求参数。入口只跳过 HTTP 身份复核，
     * 样本门槛、任务状态、参数版本和结果接受规则与手动接口完全一致。</p>
     */
    public FsrsOptimizationJobResponse startInternal(
            Long ownerUserId,
            StartFsrsOptimizationRequest request
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        FsrsUserProfile profile = profileService.getOrCreate(ownerUserId);
        FsrsOptimizationJob running = jobRepository.save(baseJob(
                ownerUserId, profile, FsrsOptimizationJobStatus.RUNNING,
                "正在回放复习历史并拟合 FSRS 权重", now
        ));

        List<StudyFlashcardReview> reviews = reviewRepository.findAllByOwnerUserId(ownerUserId);
        if (reviews.size() < MINIMUM_REVIEW_COUNT) {
            return toResponse(jobRepository.save(finish(
                    running, FsrsOptimizationJobStatus.SKIPPED, reviews.size(), 0,
                    calculateLapseRate(reviews), profile.parameters(), profile.desiredRetention(),
                    0, 0, 0, 0, false, false, null,
                    "复习样本不足，至少需要" + MINIMUM_REVIEW_COUNT + "条记录"
            )));
        }

        try {
            FsrsOptimizationResult result = parameterOptimizer.optimize(
                    profile.parameters(), profile.desiredRetention(), reviews
            );
            double recommendedRetention = recommendRetention(
                    profile.desiredRetention(), calculateLapseRate(reviews)
            );
            boolean applied = request.applyResult() && result.accepted();
            Long appliedVersion = null;
            if (applied) {
                appliedVersion = profileService.applyOptimized(
                        ownerUserId, result.parameters(), recommendedRetention,
                        "FSRS 历史拟合任务 " + running.id()
                ).version();
            }
            String message = result.accepted()
                    ? applied ? "拟合结果已通过验证并生成新参数版本" : "拟合结果已通过验证，等待用户应用"
                    : "验证集损失没有稳定改善，保留当前参数";
            return toResponse(jobRepository.save(finish(
                    running, FsrsOptimizationJobStatus.SUCCEEDED, reviews.size(),
                    result.effectiveObservationCount(), calculateLapseRate(reviews),
                    result.parameters(), recommendedRetention,
                    result.trainingLossBefore(), result.trainingLossAfter(),
                    result.validationLossBefore(), result.validationLossAfter(),
                    result.accepted(), applied, appliedVersion, message
            )));
        } catch (RuntimeException exception) {
            return toResponse(jobRepository.save(finish(
                    running, FsrsOptimizationJobStatus.FAILED, reviews.size(), 0,
                    calculateLapseRate(reviews), profile.parameters(), profile.desiredRetention(),
                    0, 0, 0, 0, false, false, null,
                    "权重拟合失败：" + safeMessage(exception)
            )));
        }
    }

    public PageResponse<FsrsOptimizationJobResponse> list(
            AgentToolExecutionContext context,
            int page,
            int pageSize
    ) {
        authorizer.authorize(context);
        int offset = (page - 1) * pageSize;
        return new PageResponse<>(
                jobRepository.findByOwnerUserId(context.ownerUserId(), offset, pageSize)
                        .stream().map(this::toResponse).toList(),
                page, pageSize, jobRepository.countByOwnerUserId(context.ownerUserId())
        );
    }

    private FsrsOptimizationJob baseJob(
            Long ownerUserId,
            FsrsUserProfile profile,
            FsrsOptimizationJobStatus status,
            String message,
            OffsetDateTime createdAt
    ) {
        return new FsrsOptimizationJob(
                null, ownerUserId, status, 0, 0, 0,
                profile.parameters(), profile.parameters(), profile.desiredRetention(),
                profile.desiredRetention(), 0, 0, 0, 0,
                false, false, null, message, createdAt, null
        );
    }

    private FsrsOptimizationJob finish(
            FsrsOptimizationJob running,
            FsrsOptimizationJobStatus status,
            int reviewCount,
            int effectiveObservationCount,
            double lapseRate,
            List<Double> recommendedParameters,
            double recommendedRetention,
            double trainingBefore,
            double trainingAfter,
            double validationBefore,
            double validationAfter,
            boolean accepted,
            boolean applied,
            Long appliedVersion,
            String message
    ) {
        return new FsrsOptimizationJob(
                running.id(), running.ownerUserId(), status, reviewCount, effectiveObservationCount,
                lapseRate, running.previousParameters(), recommendedParameters,
                running.previousDesiredRetention(), recommendedRetention,
                trainingBefore, trainingAfter, validationBefore, validationAfter,
                accepted, applied, appliedVersion, message, running.createdAt(), OffsetDateTime.now()
        );
    }

    private double calculateLapseRate(List<StudyFlashcardReview> reviews) {
        if (reviews.isEmpty()) {
            return 0;
        }
        long lapses = reviews.stream().filter(review -> review.score() < 3).count();
        return round(lapses * 100.0 / reviews.size());
    }

    private double recommendRetention(double current, double lapseRatePercent) {
        double candidate = current;
        if (lapseRatePercent > 20) {
            candidate += Math.min(0.03, (lapseRatePercent - 20) / 500.0);
        } else if (lapseRatePercent < 8) {
            candidate -= Math.min(0.02, (8 - lapseRatePercent) / 500.0);
        }
        return Math.round(Math.max(0.70, Math.min(0.99, candidate)) * 1000.0) / 1000.0;
    }

    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private FsrsOptimizationJobResponse toResponse(FsrsOptimizationJob job) {
        return new FsrsOptimizationJobResponse(
                job.id(), job.status(), job.reviewCount(), job.effectiveObservationCount(),
                job.observedLapseRate(), job.previousParameters(), job.recommendedParameters(),
                job.previousDesiredRetention(), job.recommendedDesiredRetention(),
                job.trainingLossBefore(), job.trainingLossAfter(),
                job.validationLossBefore(), job.validationLossAfter(), job.accepted(),
                job.applied(), job.appliedVersion(), job.message(), job.createdAt(), job.completedAt()
        );
    }
}
