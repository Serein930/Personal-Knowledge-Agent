package com.agentmind.study.flashcard.fsrs.service;

import com.agentmind.agent.service.AgentToolExecutionAuthorizer;
import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.common.exception.BusinessException;
import com.agentmind.common.exception.ErrorCode;
import com.agentmind.study.flashcard.fsrs.model.FsrsUserProfile;
import com.agentmind.study.flashcard.fsrs.model.FsrsUserProfileSource;
import com.agentmind.study.flashcard.fsrs.model.dto.FsrsUserProfileResponse;
import com.agentmind.study.flashcard.fsrs.model.dto.UpdateFsrsUserProfileRequest;
import com.agentmind.study.flashcard.fsrs.repository.FsrsUserProfileRepository;
import io.github.openspacedrepetition.Scheduler;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;

/** 用户级 FSRS 参数应用服务。 */
@Service
public class FsrsUserProfileService {

    private final FsrsUserProfileRepository repository;
    private final AgentToolExecutionAuthorizer authorizer;
    private final List<Double> defaultParameters;
    private final double defaultDesiredRetention;

    public FsrsUserProfileService(
            FsrsUserProfileRepository repository,
            AgentToolExecutionAuthorizer authorizer
    ) {
        this.repository = repository;
        this.authorizer = authorizer;
        Scheduler defaultScheduler = Scheduler.builder().build();
        this.defaultParameters = Arrays.stream(defaultScheduler.getParameters()).boxed().toList();
        this.defaultDesiredRetention = defaultScheduler.getDesiredRetention();
    }

    public FsrsUserProfileResponse get(AgentToolExecutionContext context) {
        authorizer.authorize(context);
        return toResponse(getOrCreate(context.ownerUserId()));
    }

    public FsrsUserProfileResponse update(
            AgentToolExecutionContext context,
            UpdateFsrsUserProfileRequest request
    ) {
        authorizer.authorize(context);
        validateParameters(request.parameters());
        FsrsUserProfile current = getOrCreate(context.ownerUserId());
        OffsetDateTime now = OffsetDateTime.now();
        FsrsUserProfile saved = repository.save(new FsrsUserProfile(
                context.ownerUserId(), request.parameters(), request.desiredRetention(),
                current.version() + 1, FsrsUserProfileSource.MANUAL,
                current.createdAt(), now
        ));
        return toResponse(saved);
    }

    /** 供评分流程读取，不重复执行 HTTP 层权限校验。 */
    public FsrsUserProfile getOrCreate(Long ownerUserId) {
        return repository.findByOwnerUserId(ownerUserId).orElseGet(() -> {
            OffsetDateTime now = OffsetDateTime.now();
            return repository.save(new FsrsUserProfile(
                    ownerUserId, defaultParameters, defaultDesiredRetention, 0,
                    FsrsUserProfileSource.DEFAULT, now, now
            ));
        });
    }

    /** 优化任务通过该入口应用结果，保留参数权重并递增可审计版本号。 */
    public FsrsUserProfile applyOptimizedRetention(Long ownerUserId, double desiredRetention) {
        FsrsUserProfile current = getOrCreate(ownerUserId);
        OffsetDateTime now = OffsetDateTime.now();
        return repository.save(new FsrsUserProfile(
                ownerUserId, current.parameters(), desiredRetention, current.version() + 1,
                FsrsUserProfileSource.OPTIMIZED, current.createdAt(), now
        ));
    }

    private void validateParameters(List<Double> parameters) {
        if (parameters.size() != defaultParameters.size()) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "FSRS 参数数量必须为" + defaultParameters.size()
            );
        }
        if (parameters.stream().anyMatch(value -> value == null || !Double.isFinite(value))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "FSRS 参数必须是有限数值");
        }
        try {
            Scheduler.builder().parameters(parameters.stream().mapToDouble(Double::doubleValue).toArray()).build();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "FSRS 参数不符合算法约束");
        }
    }

    private FsrsUserProfileResponse toResponse(FsrsUserProfile profile) {
        return new FsrsUserProfileResponse(
                profile.parameters(), profile.desiredRetention(), profile.version(),
                profile.source(), profile.updatedAt()
        );
    }
}
