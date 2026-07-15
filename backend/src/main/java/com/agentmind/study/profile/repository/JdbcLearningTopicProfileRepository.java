package com.agentmind.study.profile.repository;

import com.agentmind.study.profile.model.LearningTopicLevel;
import com.agentmind.study.profile.model.LearningTopicProfile;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 学习画像 PostgreSQL 适配器。 */
@Repository
@ConditionalOnProperty(prefix = "agentmind.agent.persistence", name = "store", havingValue = "jdbc")
public class JdbcLearningTopicProfileRepository implements LearningTopicProfileRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcLearningTopicProfileRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void replaceScope(Long ownerUserId, Long workspaceId, List<LearningTopicProfile> profiles) {
        jdbcTemplate.update(
                "delete from learning_topic_profiles where owner_user_id = ? and workspace_id = ?",
                ownerUserId, workspaceId
        );
        for (LearningTopicProfile profile : profiles) {
            jdbcTemplate.update("""
                    insert into learning_topic_profiles (
                        owner_user_id, workspace_id, topic, card_count, review_count,
                        success_rate, lapse_rate, mastery_score, level, last_reviewed_at, updated_at
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, profile.ownerUserId(), profile.workspaceId(), profile.topic(), profile.cardCount(),
                    profile.reviewCount(), profile.successRate(), profile.lapseRate(), profile.masteryScore(),
                    profile.level().name(), profile.lastReviewedAt(), profile.updatedAt());
        }
    }

    @Override
    public List<LearningTopicProfile> findByScope(Long ownerUserId, Long workspaceId) {
        return jdbcTemplate.query("""
                        select owner_user_id, workspace_id, topic, card_count, review_count,
                               success_rate, lapse_rate, mastery_score, level, last_reviewed_at, updated_at
                        from learning_topic_profiles
                        where owner_user_id = ? and workspace_id = ?
                        order by mastery_score, topic
                        """, (resultSet, rowNumber) -> new LearningTopicProfile(
                        resultSet.getLong("owner_user_id"), resultSet.getLong("workspace_id"),
                        resultSet.getString("topic"), resultSet.getInt("card_count"),
                        resultSet.getInt("review_count"), resultSet.getDouble("success_rate"),
                        resultSet.getDouble("lapse_rate"), resultSet.getDouble("mastery_score"),
                        LearningTopicLevel.valueOf(resultSet.getString("level")),
                        resultSet.getObject("last_reviewed_at", OffsetDateTime.class),
                        resultSet.getObject("updated_at", OffsetDateTime.class)
                ), ownerUserId, workspaceId);
    }
}
