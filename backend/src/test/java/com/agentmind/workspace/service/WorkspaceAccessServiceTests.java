package com.agentmind.workspace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentmind.common.exception.BusinessException;
import com.agentmind.workspace.model.WorkspaceMemberRole;
import com.agentmind.workspace.repository.InMemoryKnowledgeWorkspaceRepository;
import org.junit.jupiter.api.Test;

/** 验证知识空间成员隔离，避免仅凭路径编号读取其他用户资料。 */
class WorkspaceAccessServiceTests {

    private final WorkspaceAccessService service =
            new WorkspaceAccessService(new InMemoryKnowledgeWorkspaceRepository());

    @Test
    void ownerShouldHaveReadAndWritePermission() {
        assertThat(service.requireReadable(1L, 1L)).isEqualTo(WorkspaceMemberRole.OWNER);
        assertThat(service.requireWritable(1L, 1L)).isEqualTo(WorkspaceMemberRole.OWNER);
    }

    @Test
    void nonMemberShouldBeRejected() {
        assertThatThrownBy(() -> service.requireReadable(2L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不是该知识空间成员");
    }
}
