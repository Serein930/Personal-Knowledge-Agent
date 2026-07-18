package com.agentmind.user.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.agentmind.common.exception.BusinessException;
import com.agentmind.common.exception.ErrorCode;
import com.agentmind.user.repository.UserWorkspacePreferenceRepository;
import com.agentmind.workspace.service.WorkspaceAccessService;
import org.junit.jupiter.api.Test;

/** 用户偏好服务的知识空间权限边界测试。 */
class UserWorkspacePreferenceServiceTests {

    @Test
    void shouldNotReadPreferenceWhenWorkspaceAccessIsDenied() {
        UserWorkspacePreferenceRepository repository = mock(UserWorkspacePreferenceRepository.class);
        WorkspaceAccessService accessService = mock(WorkspaceAccessService.class);
        when(accessService.requireReadable(9L, 88L))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN, "无权访问知识空间"));
        UserWorkspacePreferenceService service = new UserWorkspacePreferenceService(
                repository,
                accessService,
                "gpt-4o-mini",
                "text-embedding-3-small",
                com.agentmind.user.model.CitationPolicy.REQUIRED,
                5
        );

        assertThatThrownBy(() -> service.get(9L, 88L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权访问知识空间");
        verifyNoInteractions(repository);
    }
}
