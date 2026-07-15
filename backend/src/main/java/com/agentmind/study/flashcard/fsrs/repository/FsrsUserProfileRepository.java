package com.agentmind.study.flashcard.fsrs.repository;

import com.agentmind.study.flashcard.fsrs.model.FsrsUserProfile;
import java.util.Optional;

/** 用户级 FSRS 参数档案存储端口。 */
public interface FsrsUserProfileRepository {

    Optional<FsrsUserProfile> findByOwnerUserId(Long ownerUserId);

    FsrsUserProfile save(FsrsUserProfile profile);
}
