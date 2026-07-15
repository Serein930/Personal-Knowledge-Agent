package com.agentmind.study.flashcard.fsrs.repository;

import com.agentmind.study.flashcard.fsrs.model.FsrsProfileVersion;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/** FSRS 参数历史版本内存适配器。 */
@Repository
@ConditionalOnProperty(prefix = "agentmind.agent.persistence", name = "store", havingValue = "memory", matchIfMissing = true)
public class InMemoryFsrsProfileVersionRepository implements FsrsProfileVersionRepository {

    private final ConcurrentHashMap<String, FsrsProfileVersion> versions = new ConcurrentHashMap<>();

    @Override
    public FsrsProfileVersion saveIfAbsent(FsrsProfileVersion version) {
        return versions.computeIfAbsent(key(version.ownerUserId(), version.version()), ignored -> version);
    }

    @Override
    public Optional<FsrsProfileVersion> findByOwnerUserIdAndVersion(Long ownerUserId, long version) {
        return Optional.ofNullable(versions.get(key(ownerUserId, version)));
    }

    @Override
    public List<FsrsProfileVersion> findByOwnerUserId(Long ownerUserId, int offset, int limit) {
        return versions.values().stream()
                .filter(version -> ownerUserId.equals(version.ownerUserId()))
                .sorted(Comparator.comparingLong(FsrsProfileVersion::version).reversed())
                .skip(offset).limit(limit).toList();
    }

    @Override
    public long countByOwnerUserId(Long ownerUserId) {
        return versions.values().stream().filter(version -> ownerUserId.equals(version.ownerUserId())).count();
    }

    private String key(Long ownerUserId, long version) {
        return ownerUserId + ":" + version;
    }
}
