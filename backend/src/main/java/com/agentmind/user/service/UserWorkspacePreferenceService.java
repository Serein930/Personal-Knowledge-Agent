package com.agentmind.user.service;

import com.agentmind.common.exception.BusinessException;
import com.agentmind.common.exception.ErrorCode;
import com.agentmind.user.model.CitationPolicy;
import com.agentmind.user.model.UserWorkspacePreference;
import com.agentmind.user.model.dto.UpdateUserWorkspacePreferenceRequest;
import com.agentmind.user.model.dto.UserWorkspacePreferenceResponse;
import com.agentmind.user.repository.UserWorkspacePreferenceRepository;
import com.agentmind.workspace.service.WorkspaceAccessService;
import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户知识空间偏好应用服务。
 *
 * <p>模型名称属于用户选择，不代表前端可以修改供应商密钥。真实模型适配器仍由后端受保护配置创建，
 * RAG 与文档摄取链路会消费这里保存的非敏感模型标识，并通过 Spring AI 请求级选项完成路由。</p>
 */
@Service
public class UserWorkspacePreferenceService {

    private final UserWorkspacePreferenceRepository repository;
    private final WorkspaceAccessService workspaceAccessService;
    private final String defaultChatModel;
    private final String defaultEmbeddingModel;
    private final CitationPolicy defaultCitationPolicy;
    private final int defaultTopK;

    public UserWorkspacePreferenceService(
            UserWorkspacePreferenceRepository repository,
            WorkspaceAccessService workspaceAccessService,
            @Value("${agentmind.preferences.default-chat-model:gpt-4o-mini}")
            String defaultChatModel,
            @Value("${agentmind.preferences.default-embedding-model:text-embedding-3-small}")
            String defaultEmbeddingModel,
            @Value("${agentmind.preferences.default-citation-policy:REQUIRED}")
            CitationPolicy defaultCitationPolicy,
            @Value("${agentmind.preferences.default-top-k:5}")
            int defaultTopK
    ) {
        this.repository = repository;
        this.workspaceAccessService = workspaceAccessService;
        this.defaultChatModel = defaultChatModel;
        this.defaultEmbeddingModel = defaultEmbeddingModel;
        this.defaultCitationPolicy = defaultCitationPolicy;
        this.defaultTopK = defaultTopK;
    }

    /** 查询已保存偏好；首次访问时返回部署环境定义的默认值。 */
    @Transactional(readOnly = true)
    public UserWorkspacePreferenceResponse get(Long userId, Long workspaceId) {
        workspaceAccessService.requireReadable(userId, workspaceId);
        return repository.findByUserIdAndWorkspaceId(userId, workspaceId)
                .map(preference -> toResponse(preference, true))
                .orElseGet(() -> defaultResponse(workspaceId));
    }

    /** 使用乐观锁保存偏好，版本冲突时要求调用方重新加载。 */
    @Transactional
    public UserWorkspacePreferenceResponse update(
            Long userId,
            Long workspaceId,
            UpdateUserWorkspacePreferenceRequest request
    ) {
        workspaceAccessService.requireReadable(userId, workspaceId);
        validateExpectedVersion(userId, workspaceId, request.expectedVersion());

        OffsetDateTime now = OffsetDateTime.now();
        UserWorkspacePreference candidate = new UserWorkspacePreference(
                userId,
                workspaceId,
                request.chatModel().trim(),
                request.embeddingModel().trim(),
                request.citationPolicy(),
                request.defaultTopK(),
                request.expectedVersion(),
                now,
                now
        );
        UserWorkspacePreference saved = repository.save(candidate, request.expectedVersion())
                .orElseThrow(this::versionConflict);
        return toResponse(saved, true);
    }

    private void validateExpectedVersion(Long userId, Long workspaceId, long expectedVersion) {
        repository.findByUserIdAndWorkspaceId(userId, workspaceId).ifPresentOrElse(
                current -> {
                    if (current.version() != expectedVersion) {
                        throw versionConflict();
                    }
                },
                () -> {
                    if (expectedVersion != 0) {
                        throw versionConflict();
                    }
                }
        );
    }

    private UserWorkspacePreferenceResponse defaultResponse(Long workspaceId) {
        return new UserWorkspacePreferenceResponse(
                workspaceId,
                defaultChatModel,
                defaultEmbeddingModel,
                defaultCitationPolicy,
                defaultTopK,
                0,
                false,
                null
        );
    }

    private UserWorkspacePreferenceResponse toResponse(
            UserWorkspacePreference preference,
            boolean persisted
    ) {
        return new UserWorkspacePreferenceResponse(
                preference.workspaceId(),
                preference.chatModel(),
                preference.embeddingModel(),
                preference.citationPolicy(),
                preference.defaultTopK(),
                preference.version(),
                persisted,
                preference.updatedAt()
        );
    }

    private BusinessException versionConflict() {
        return new BusinessException(ErrorCode.RESOURCE_CONFLICT, "偏好设置已被其他请求更新，请重新加载后再保存");
    }
}
