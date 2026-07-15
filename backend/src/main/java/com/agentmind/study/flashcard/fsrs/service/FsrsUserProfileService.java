package com.agentmind.study.flashcard.fsrs.service;

import com.agentmind.agent.service.AgentToolExecutionAuthorizer;
import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.common.exception.BusinessException;
import com.agentmind.common.exception.ErrorCode;
import com.agentmind.common.response.PageResponse;
import com.agentmind.study.flashcard.fsrs.model.FsrsProfileVersion;
import com.agentmind.study.flashcard.fsrs.model.FsrsUserProfile;
import com.agentmind.study.flashcard.fsrs.model.FsrsUserProfileSource;
import com.agentmind.study.flashcard.fsrs.model.dto.FsrsProfileVersionResponse;
import com.agentmind.study.flashcard.fsrs.model.dto.FsrsUserProfileResponse;
import com.agentmind.study.flashcard.fsrs.model.dto.RollbackFsrsProfileRequest;
import com.agentmind.study.flashcard.fsrs.model.dto.UpdateFsrsUserProfileRequest;
import com.agentmind.study.flashcard.fsrs.repository.FsrsProfileVersionRepository;
import com.agentmind.study.flashcard.fsrs.repository.FsrsUserProfileRepository;
import io.github.openspacedrepetition.Scheduler;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户级 FSRS 参数应用服务。
 *
 * <p>当前参数表只保存生效版本，历史版本表保存不可变快照。更新、优化和回滚都通过同一入口生成
 * 新版本，因此回滚不会删除优化证据，也不会让旧的卡片状态快照错误匹配新参数。</p>
 */
@Service
public class FsrsUserProfileService {

    private final FsrsUserProfileRepository repository;
    private final FsrsProfileVersionRepository versionRepository;
    private final AgentToolExecutionAuthorizer authorizer;
    private final List<Double> defaultParameters;
    private final double defaultDesiredRetention;

    public FsrsUserProfileService(
            FsrsUserProfileRepository repository,
            FsrsProfileVersionRepository versionRepository,
            AgentToolExecutionAuthorizer authorizer
    ) {
        this.repository = repository;
        this.versionRepository = versionRepository;
        this.authorizer = authorizer;
        Scheduler defaultScheduler = Scheduler.builder().build();
        this.defaultParameters = Arrays.stream(defaultScheduler.getParameters()).boxed().toList();
        this.defaultDesiredRetention = defaultScheduler.getDesiredRetention();
    }

    public FsrsUserProfileResponse get(AgentToolExecutionContext context) {
        authorizer.authorize(context);
        return toResponse(getOrCreate(context.ownerUserId()));
    }

    @Transactional
    public FsrsUserProfileResponse update(
            AgentToolExecutionContext context,
            UpdateFsrsUserProfileRequest request
    ) {
        authorizer.authorize(context);
        validateParameters(request.parameters());
        FsrsUserProfile current = getOrCreate(context.ownerUserId());
        OffsetDateTime now = OffsetDateTime.now();
        FsrsUserProfile saved = saveVersioned(new FsrsUserProfile(
                context.ownerUserId(), request.parameters(), request.desiredRetention(),
                current.version() + 1, FsrsUserProfileSource.MANUAL,
                current.createdAt(), now
        ), "用户手工调整参数");
        return toResponse(saved);
    }

    /** 供评分流程读取，不重复执行 HTTP 层权限校验。 */
    @Transactional
    public FsrsUserProfile getOrCreate(Long ownerUserId) {
        return repository.findByOwnerUserId(ownerUserId).orElseGet(() -> {
            OffsetDateTime now = OffsetDateTime.now();
            return saveVersioned(new FsrsUserProfile(
                    ownerUserId, defaultParameters, defaultDesiredRetention, 0,
                    FsrsUserProfileSource.DEFAULT, now, now
            ), "初始化默认参数");
        });
    }

    /** 优化任务通过该入口同时应用权重和保持率，并生成可审计版本。 */
    @Transactional
    public FsrsUserProfile applyOptimized(
            Long ownerUserId,
            List<Double> parameters,
            double desiredRetention,
            String changeReason
    ) {
        validateParameters(parameters);
        FsrsUserProfile current = getOrCreate(ownerUserId);
        OffsetDateTime now = OffsetDateTime.now();
        return saveVersioned(new FsrsUserProfile(
                ownerUserId, parameters, desiredRetention, current.version() + 1,
                FsrsUserProfileSource.OPTIMIZED, current.createdAt(), now
        ), changeReason);
    }

    public PageResponse<FsrsProfileVersionResponse> listVersions(
            AgentToolExecutionContext context,
            int page,
            int pageSize
    ) {
        authorizer.authorize(context);
        int offset = Math.multiplyExact(page - 1, pageSize);
        return new PageResponse<>(
                versionRepository.findByOwnerUserId(context.ownerUserId(), offset, pageSize)
                        .stream().map(this::toVersionResponse).toList(),
                page, pageSize, versionRepository.countByOwnerUserId(context.ownerUserId())
        );
    }

    @Transactional
    public FsrsUserProfileResponse rollback(
            AgentToolExecutionContext context,
            RollbackFsrsProfileRequest request
    ) {
        authorizer.authorize(context);
        FsrsUserProfile current = getOrCreate(context.ownerUserId());
        if (current.version() != request.expectedCurrentVersion()) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "FSRS 参数版本已变化，请刷新后重试");
        }
        FsrsProfileVersion target = versionRepository.findByOwnerUserIdAndVersion(
                        context.ownerUserId(), request.targetVersion()
                )
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "目标 FSRS 参数版本不存在"));
        OffsetDateTime now = OffsetDateTime.now();
        FsrsUserProfile rolledBack = saveVersioned(new FsrsUserProfile(
                current.ownerUserId(), target.parameters(), target.desiredRetention(),
                current.version() + 1, FsrsUserProfileSource.ROLLBACK,
                current.createdAt(), now
        ), "从版本 " + target.version() + " 回滚");
        return toResponse(rolledBack);
    }

    private FsrsUserProfile saveVersioned(FsrsUserProfile profile, String reason) {
        FsrsUserProfile saved = repository.save(profile);
        versionRepository.saveIfAbsent(new FsrsProfileVersion(
                saved.ownerUserId(), saved.version(), saved.parameters(), saved.desiredRetention(),
                saved.source(), reason, saved.updatedAt()
        ));
        return saved;
    }

    private void validateParameters(List<Double> parameters) {
        if (parameters.size() != defaultParameters.size()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "FSRS 参数数量必须为" + defaultParameters.size());
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

    private FsrsProfileVersionResponse toVersionResponse(FsrsProfileVersion version) {
        return new FsrsProfileVersionResponse(
                version.version(), version.parameters(), version.desiredRetention(),
                version.source(), version.changeReason(), version.createdAt()
        );
    }
}
