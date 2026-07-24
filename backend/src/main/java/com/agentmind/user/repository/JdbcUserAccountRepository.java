package com.agentmind.user.repository;

import com.agentmind.user.model.UserAccount;
import com.agentmind.user.model.UserRole;
import com.agentmind.user.model.UserStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/** PostgreSQL 用户仓储，用户名和邮箱唯一性由数据库约束最终兜底。 */
@Repository
@ConditionalOnProperty(prefix = "agentmind.core.persistence", name = "store", havingValue = "jdbc")
public class JdbcUserAccountRepository implements UserAccountRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcUserAccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public UserAccount create(String username, String displayName, String email, String passwordHash) {
        OffsetDateTime now = OffsetDateTime.now();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                    insert into app_user (
                        username, display_name, email, password_hash, role, status, created_at, updated_at
                    ) values (?, ?, ?, ?, 'USER', 'ACTIVE', ?, ?)
                    """, new String[]{"id"});
            statement.setString(1, username);
            statement.setString(2, displayName);
            statement.setString(3, email);
            statement.setString(4, passwordHash);
            statement.setObject(5, now);
            statement.setObject(6, now);
            return statement;
        }, keyHolder);
        Number id = keyHolder.getKey();
        if (id == null) {
            throw new IllegalStateException("创建用户后未返回主键");
        }
        return new UserAccount(id.longValue(), username, displayName, email, passwordHash,
                UserRole.USER, UserStatus.ACTIVE, now, now);
    }

    @Override
    public Optional<UserAccount> findById(Long userId) {
        return jdbcTemplate.query("select * from app_user where id = ?", this::mapUser, userId).stream().findFirst();
    }

    @Override
    public Optional<UserAccount> findByUsername(String username) {
        return jdbcTemplate.query("select * from app_user where lower(username) = lower(?)", this::mapUser, username)
                .stream().findFirst();
    }

    @Override
    public boolean existsByUsernameOrEmail(String username, String email) {
        Long count = jdbcTemplate.queryForObject("""
                select count(*) from app_user
                where lower(username) = lower(?) or lower(email) = lower(?)
                """, Long.class, username, email);
        return count != null && count > 0;
    }

    @Override
    public boolean updatePasswordHash(Long userId, String passwordHash) {
        return jdbcTemplate.update(
                "update app_user set password_hash = ?, updated_at = ? where id = ? and status = 'ACTIVE'",
                passwordHash, OffsetDateTime.now(), userId
        ) == 1;
    }

    private UserAccount mapUser(ResultSet resultSet, int rowNumber) throws SQLException {
        return new UserAccount(
                resultSet.getLong("id"),
                resultSet.getString("username"),
                resultSet.getString("display_name"),
                resultSet.getString("email"),
                resultSet.getString("password_hash"),
                UserRole.valueOf(resultSet.getString("role")),
                UserStatus.valueOf(resultSet.getString("status")),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class)
        );
    }
}
