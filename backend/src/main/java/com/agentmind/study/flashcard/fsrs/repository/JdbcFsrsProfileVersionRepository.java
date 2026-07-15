package com.agentmind.study.flashcard.fsrs.repository;

import com.agentmind.study.flashcard.fsrs.model.FsrsProfileVersion;
import com.agentmind.study.flashcard.fsrs.model.FsrsUserProfileSource;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** FSRS 参数历史版本 PostgreSQL 适配器。 */
@Repository
@ConditionalOnProperty(prefix = "agentmind.agent.persistence", name = "store", havingValue = "jdbc")
public class JdbcFsrsProfileVersionRepository implements FsrsProfileVersionRepository {

    private static final String COLUMNS = "owner_user_id, version, parameters::text, desired_retention, "
            + "source, change_reason, created_at";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcFsrsProfileVersionRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public FsrsProfileVersion saveIfAbsent(FsrsProfileVersion version) {
        return jdbcTemplate.query("""
                        insert into fsrs_user_profile_versions (
                            owner_user_id, version, parameters, desired_retention,
                            source, change_reason, created_at
                        ) values (?, ?, cast(? as jsonb), ?, ?, ?, ?)
                        on conflict (owner_user_id, version) do nothing
                        returning %s
                        """.formatted(COLUMNS), this::mapVersion,
                version.ownerUserId(), version.version(), writeParameters(version.parameters()),
                version.desiredRetention(), version.source().name(), version.changeReason(),
                version.createdAt()).stream().findFirst()
                .orElseGet(() -> findByOwnerUserIdAndVersion(version.ownerUserId(), version.version())
                        .orElseThrow(() -> new IllegalStateException("保存 FSRS 参数版本失败")));
    }

    @Override
    public Optional<FsrsProfileVersion> findByOwnerUserIdAndVersion(Long ownerUserId, long version) {
        return jdbcTemplate.query(
                "select " + COLUMNS + " from fsrs_user_profile_versions where owner_user_id = ? and version = ?",
                this::mapVersion, ownerUserId, version
        ).stream().findFirst();
    }

    @Override
    public List<FsrsProfileVersion> findByOwnerUserId(Long ownerUserId, int offset, int limit) {
        return jdbcTemplate.query(
                "select " + COLUMNS + " from fsrs_user_profile_versions "
                        + "where owner_user_id = ? order by version desc offset ? limit ?",
                this::mapVersion, ownerUserId, offset, limit
        );
    }

    @Override
    public long countByOwnerUserId(Long ownerUserId) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from fsrs_user_profile_versions where owner_user_id = ?",
                Long.class, ownerUserId
        );
        return count == null ? 0 : count;
    }

    private FsrsProfileVersion mapVersion(ResultSet resultSet, int rowNumber) throws SQLException {
        try {
            List<Double> parameters = objectMapper.readValue(
                    resultSet.getString("parameters"), new TypeReference<>() {
                    }
            );
            return new FsrsProfileVersion(
                    resultSet.getLong("owner_user_id"), resultSet.getLong("version"), parameters,
                    resultSet.getDouble("desired_retention"),
                    FsrsUserProfileSource.valueOf(resultSet.getString("source")),
                    resultSet.getString("change_reason"),
                    resultSet.getObject("created_at", OffsetDateTime.class)
            );
        } catch (JsonProcessingException exception) {
            throw new SQLException("读取 FSRS 参数版本 JSON 失败", exception);
        }
    }

    private String writeParameters(List<Double> parameters) {
        try {
            return objectMapper.writeValueAsString(parameters);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("序列化 FSRS 参数版本失败", exception);
        }
    }
}
