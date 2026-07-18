package com.agentmind.user.repository;

import com.agentmind.user.model.UserWorkspacePreference;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/** 零依赖开发和常规单元测试使用的用户偏好仓储。 */
@Repository
@ConditionalOnProperty(prefix = "agentmind.core.persistence", name = "store", havingValue = "memory", matchIfMissing = true)
public class InMemoryUserWorkspacePreferenceRepository implements UserWorkspacePreferenceRepository {

    private final Map<String, UserWorkspacePreference> preferences = new ConcurrentHashMap<>();

    @Override
    public Optional<UserWorkspacePreference> findByUserIdAndWorkspaceId(Long userId, Long workspaceId) {
        return Optional.ofNullable(preferences.get(key(userId, workspaceId)));
    }

    @Override
    public synchronized Optional<UserWorkspacePreference> save(
            UserWorkspacePreference preference,
            long expectedVersion
    ) {
        String key = key(preference.userId(), preference.workspaceId());
        UserWorkspacePreference current = preferences.get(key);
        if (current != null && current.version() != expectedVersion) {
            return Optional.empty();
        }
        if (current == null && expectedVersion != 0) {
            return Optional.empty();
        }

        OffsetDateTime now = OffsetDateTime.now();
        UserWorkspacePreference saved = new UserWorkspacePreference(
                preference.userId(),
                preference.workspaceId(),
                preference.chatModel(),
                preference.embeddingModel(),
                preference.citationPolicy(),
                preference.defaultTopK(),
                current == null ? 1 : current.version() + 1,
                current == null ? now : current.createdAt(),
                now
        );
        preferences.put(key, saved);
        return Optional.of(saved);
    }

    private String key(Long userId, Long workspaceId) {
        return userId + ":" + workspaceId;
    }
}
