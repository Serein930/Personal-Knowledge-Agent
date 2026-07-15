package com.agentmind.study.profile.repository;

import com.agentmind.study.profile.model.LearningTopicProfile;
import java.util.List;

/** 学习画像快照存储端口。 */
public interface LearningTopicProfileRepository {

    void replaceScope(Long ownerUserId, Long workspaceId, List<LearningTopicProfile> profiles);

    List<LearningTopicProfile> findByScope(Long ownerUserId, Long workspaceId);
}
