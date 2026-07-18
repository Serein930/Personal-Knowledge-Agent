package com.agentmind.user.repository;

import com.agentmind.user.model.UserWorkspacePreference;
import java.util.Optional;

/** 用户知识空间偏好持久化端口。 */
public interface UserWorkspacePreferenceRepository {

    Optional<UserWorkspacePreference> findByUserIdAndWorkspaceId(Long userId, Long workspaceId);

    /**
     * 按预期版本新增或更新偏好。
     *
     * <p>版本不匹配时返回空，调用方应提示用户重新加载，不能静默覆盖其他页面刚保存的设置。</p>
     */
    Optional<UserWorkspacePreference> save(UserWorkspacePreference preference, long expectedVersion);
}
