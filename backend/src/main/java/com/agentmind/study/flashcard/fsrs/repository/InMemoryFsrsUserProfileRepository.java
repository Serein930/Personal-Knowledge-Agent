package com.agentmind.study.flashcard.fsrs.repository;

import com.agentmind.study.flashcard.fsrs.model.FsrsUserProfile;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/** 用户级 FSRS 参数档案内存适配器。 */
@Repository
@ConditionalOnProperty(prefix = "agentmind.agent.persistence", name = "store", havingValue = "memory", matchIfMissing = true)
public class InMemoryFsrsUserProfileRepository implements FsrsUserProfileRepository {

    private final ConcurrentHashMap<Long, FsrsUserProfile> profiles = new ConcurrentHashMap<>();

    @Override
    public Optional<FsrsUserProfile> findByOwnerUserId(Long ownerUserId) {
        return Optional.ofNullable(profiles.get(ownerUserId));
    }

    @Override
    public FsrsUserProfile save(FsrsUserProfile profile) {
        profiles.put(profile.ownerUserId(), profile);
        return profile;
    }
}
