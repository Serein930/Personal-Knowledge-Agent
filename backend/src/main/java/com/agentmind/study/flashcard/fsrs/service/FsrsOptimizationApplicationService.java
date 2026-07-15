package com.agentmind.study.flashcard.fsrs.service;

import com.agentmind.agent.service.AgentToolExecutionAuthorizer;
import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.common.response.PageResponse;
import com.agentmind.study.flashcard.fsrs.model.FsrsOptimizationJob;
import com.agentmind.study.flashcard.fsrs.model.FsrsOptimizationJobStatus;
import com.agentmind.study.flashcard.fsrs.model.FsrsUserProfile;
import com.agentmind.study.flashcard.fsrs.model.dto.FsrsOptimizationJobResponse;
import com.agentmind.study.flashcard.fsrs.model.dto.StartFsrsOptimizationRequest;
import com.agentmind.study.flashcard.fsrs.repository.FsrsOptimizationJobRepository;
import com.agentmind.study.flashcard.model.StudyFlashcardReview;
import com.agentmind.study.flashcard.repository.StudyFlashcardReviewRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * FSRS 历史数据优化任务服务。
 *
 * <p>当前本地优化器使用用户全部知识空间的复习事实校准期望保持率。样本不足时任务明确标记为跳过，
 * 不伪造权重优化结果。未来可把专用权重优化器接在该服务前，任务接口和审计数据无需变化。</p>
 */
@Service
public class FsrsOptimizationApplicationService {

    private static final int MINIMUM_REVIEW_COUNT = 20;

    private final FsrsOptimizationJobRepository jobRepository;
    private final StudyFlashcardReviewRepository reviewRepository;
    private final FsrsUserProfileService profileService;
    private final AgentToolExecutionAuthorizer authorizer;

    public FsrsOptimizationApplicationService(
            FsrsOptimizationJobRepository jobRepository,
            StudyFlashcardReviewRepository reviewRepository,
            FsrsUserProfileService profileService,
            AgentToolExecutionAuthorizer authorizer
    ) {
        this.jobRepository = jobRepository;
        this.reviewRepository = reviewRepository;
        this.profileService = profileService;
        this.authorizer = authorizer;
    }

    public FsrsOptimizationJobResponse start(
            AgentToolExecutionContext context,
            StartFsrsOptimizationRequest request
    ) {
        authorizer.authorize(context);
        OffsetDateTime now = OffsetDateTime.now();
        FsrsUserProfile profile = profileService.getOrCreate(context.ownerUserId());
        FsrsOptimizationJob running = jobRepository.save(new FsrsOptimizationJob(
                null, context.ownerUserId(), FsrsOptimizationJobStatus.RUNNING,
                0, 0, profile.desiredRetention(), profile.desiredRetention(),
                false, "正在分析用户复习历史", now, null
        ));

        List<StudyFlashcardReview> reviews = reviewRepository.findAllByOwnerUserId(context.ownerUserId());
        if (reviews.size() < MINIMUM_REVIEW_COUNT) {
            return toResponse(jobRepository.save(new FsrsOptimizationJob(
                    running.id(), running.ownerUserId(), FsrsOptimizationJobStatus.SKIPPED,
                    reviews.size(), calculateLapseRate(reviews), profile.desiredRetention(),
                    profile.desiredRetention(), false,
                    "复习样本不足，至少需要" + MINIMUM_REVIEW_COUNT + "条记录",
                    running.createdAt(), OffsetDateTime.now()
            )));
        }

        double lapseRate = calculateLapseRate(reviews);
        double recommended = recommendRetention(profile.desiredRetention(), lapseRate);
        boolean changed = Math.abs(recommended - profile.desiredRetention()) >= 0.001;
        boolean applied = request.applyResult() && changed;
        if (applied) {
            profileService.applyOptimizedRetention(context.ownerUserId(), recommended);
        }
        String message = changed
                ? applied ? "推荐保持率已应用" : "已生成推荐保持率，等待用户确认应用"
                : "当前保持率与历史表现匹配，无需调整";
        return toResponse(jobRepository.save(new FsrsOptimizationJob(
                running.id(), running.ownerUserId(), FsrsOptimizationJobStatus.SUCCEEDED,
                reviews.size(), lapseRate, profile.desiredRetention(), recommended,
                applied, message, running.createdAt(), OffsetDateTime.now()
        )));
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

    private double calculateLapseRate(List<StudyFlashcardReview> reviews) {
        if (reviews.isEmpty()) {
            return 0;
        }
        long lapses = reviews.stream().filter(review -> review.score() < 3).count();
        return round(lapses * 100.0 / reviews.size());
    }

    private double recommendRetention(double current, double lapseRatePercent) {
        // 遗忘率高于 20% 时提高保持率并缩短间隔；低于 8% 时小幅降低保持率以减少复习负担。
        double candidate = current;
        if (lapseRatePercent > 20) {
            candidate += Math.min(0.03, (lapseRatePercent - 20) / 500.0);
        } else if (lapseRatePercent < 8) {
            candidate -= Math.min(0.02, (8 - lapseRatePercent) / 500.0);
        }
        return Math.round(Math.max(0.70, Math.min(0.99, candidate)) * 1000.0) / 1000.0;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private FsrsOptimizationJobResponse toResponse(FsrsOptimizationJob job) {
        return new FsrsOptimizationJobResponse(
                job.id(), job.status(), job.reviewCount(), job.observedLapseRate(),
                job.previousDesiredRetention(), job.recommendedDesiredRetention(),
                job.applied(), job.message(), job.createdAt(), job.completedAt()
        );
    }
}
