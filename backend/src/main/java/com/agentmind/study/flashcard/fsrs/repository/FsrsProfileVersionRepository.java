package com.agentmind.study.flashcard.fsrs.repository;

import com.agentmind.study.flashcard.fsrs.model.FsrsProfileVersion;
import java.util.List;
import java.util.Optional;

/** FSRS 参数历史版本存储端口。 */
public interface FsrsProfileVersionRepository {

    FsrsProfileVersion saveIfAbsent(FsrsProfileVersion version);

    Optional<FsrsProfileVersion> findByOwnerUserIdAndVersion(Long ownerUserId, long version);

    List<FsrsProfileVersion> findByOwnerUserId(Long ownerUserId, int offset, int limit);

    long countByOwnerUserId(Long ownerUserId);
}
