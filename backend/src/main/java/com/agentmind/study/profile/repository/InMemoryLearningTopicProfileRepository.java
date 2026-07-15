package com.agentmind.study.profile.repository;

import com.agentmind.study.profile.model.LearningTopicProfile;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/** 学习画像内存适配器。 */
@Repository
@ConditionalOnProperty(prefix = "agentmind.agent.persistence", name = "store", havingValue = "memory", matchIfMissing = true)
public class InMemoryLearningTopicProfileRepository implements LearningTopicProfileRepository {

    private final ConcurrentHashMap<String, List<LearningTopicProfile>> profiles = new ConcurrentHashMap<>();

    @Override
    public void replaceScope(Long ownerUserId, Long workspaceId, List<LearningTopicProfile> values) {
        profiles.put(key(ownerUserId, workspaceId), List.copyOf(values));
    }

    @Override
    public List<LearningTopicProfile> findByScope(Long ownerUserId, Long workspaceId) {
        return profiles.getOrDefault(key(ownerUserId, workspaceId), List.of());
    }

    private String key(Long ownerUserId, Long workspaceId) {
        return ownerUserId + ":" + workspaceId;
    }
}
