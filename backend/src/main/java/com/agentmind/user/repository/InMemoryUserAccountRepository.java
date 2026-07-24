package com.agentmind.user.repository;

import com.agentmind.user.model.UserAccount;
import com.agentmind.user.model.UserRole;
import com.agentmind.user.model.UserStatus;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/** 无数据库测试模式使用的用户仓储。 */
@Repository
@ConditionalOnProperty(prefix = "agentmind.core.persistence", name = "store", havingValue = "memory", matchIfMissing = true)
public class InMemoryUserAccountRepository implements UserAccountRepository {

    private final AtomicLong idGenerator = new AtomicLong(1);
    private final Map<Long, UserAccount> users = new ConcurrentHashMap<>();

    public InMemoryUserAccountRepository() {
        OffsetDateTime now = OffsetDateTime.now();
        users.put(1L, new UserAccount(1L, "demo", "演示用户", "demo@agentmind.local", "",
                UserRole.USER, UserStatus.ACTIVE, now, now));
    }

    @Override
    public synchronized UserAccount create(String username, String displayName, String email, String passwordHash) {
        long id = idGenerator.incrementAndGet();
        OffsetDateTime now = OffsetDateTime.now();
        UserAccount user = new UserAccount(id, username, displayName, email, passwordHash,
                UserRole.USER, UserStatus.ACTIVE, now, now);
        users.put(id, user);
        return user;
    }

    @Override
    public Optional<UserAccount> findById(Long userId) {
        return Optional.ofNullable(users.get(userId));
    }

    @Override
    public Optional<UserAccount> findByUsername(String username) {
        return users.values().stream().filter(user -> user.username().equalsIgnoreCase(username)).findFirst();
    }

    @Override
    public boolean existsByUsernameOrEmail(String username, String email) {
        return users.values().stream().anyMatch(user -> user.username().equalsIgnoreCase(username)
                || user.email().equalsIgnoreCase(email));
    }

    @Override
    public synchronized boolean updatePasswordHash(Long userId, String passwordHash) {
        UserAccount current = users.get(userId);
        if (current == null) {
            return false;
        }
        OffsetDateTime now = OffsetDateTime.now();
        users.put(userId, new UserAccount(
                current.id(), current.username(), current.displayName(), current.email(), passwordHash,
                current.role(), current.status(), current.createdAt(), now
        ));
        return true;
    }
}
