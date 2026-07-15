package com.agentmind.study.flashcard.fsrs.repository;

import com.agentmind.study.flashcard.fsrs.model.FsrsUserProfile;
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

/** 用户级 FSRS 参数档案 PostgreSQL 适配器。 */
@Repository
@ConditionalOnProperty(prefix = "agentmind.agent.persistence", name = "store", havingValue = "jdbc")
public class JdbcFsrsUserProfileRepository implements FsrsUserProfileRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcFsrsUserProfileRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<FsrsUserProfile> findByOwnerUserId(Long ownerUserId) {
        return jdbcTemplate.query("""
                        select owner_user_id, parameters::text, desired_retention, version,
                               source, created_at, updated_at
                        from fsrs_user_profiles where owner_user_id = ?
                        """, this::mapProfile, ownerUserId)
                .stream()
                .findFirst();
    }

    @Override
    public FsrsUserProfile save(FsrsUserProfile profile) {
        return jdbcTemplate.query("""
                        insert into fsrs_user_profiles (
                            owner_user_id, parameters, desired_retention, version,
                            source, created_at, updated_at
                        ) values (?, cast(? as jsonb), ?, ?, ?, ?, ?)
                        on conflict (owner_user_id) do update
                        set parameters = excluded.parameters,
                            desired_retention = excluded.desired_retention,
                            version = excluded.version,
                            source = excluded.source,
                            updated_at = excluded.updated_at
                        returning owner_user_id, parameters::text, desired_retention, version,
                                  source, created_at, updated_at
                        """, this::mapProfile,
                profile.ownerUserId(), writeParameters(profile.parameters()), profile.desiredRetention(),
                profile.version(), profile.source().name(), profile.createdAt(), profile.updatedAt()).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("保存 FSRS 用户参数失败"));
    }

    private FsrsUserProfile mapProfile(ResultSet resultSet, int rowNumber) throws SQLException {
        try {
            List<Double> parameters = objectMapper.readValue(
                    resultSet.getString("parameters"), new TypeReference<>() {
                    }
            );
            return new FsrsUserProfile(
                    resultSet.getLong("owner_user_id"), parameters,
                    resultSet.getDouble("desired_retention"), resultSet.getLong("version"),
                    FsrsUserProfileSource.valueOf(resultSet.getString("source")),
                    resultSet.getObject("created_at", OffsetDateTime.class),
                    resultSet.getObject("updated_at", OffsetDateTime.class)
            );
        } catch (JsonProcessingException exception) {
            throw new SQLException("读取 FSRS 参数 JSON 失败", exception);
        }
    }

    private String writeParameters(List<Double> parameters) {
        try {
            return objectMapper.writeValueAsString(parameters);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("序列化 FSRS 参数失败", exception);
        }
    }
}
