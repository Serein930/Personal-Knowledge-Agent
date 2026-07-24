package com.agentmind.user.repository;

import com.agentmind.user.model.UserAccount;
import java.util.Optional;

/** 用户账号持久化端口。 */
public interface UserAccountRepository {

    UserAccount create(String username, String displayName, String email, String passwordHash);

    Optional<UserAccount> findById(Long userId);

    Optional<UserAccount> findByUsername(String username);

    boolean existsByUsernameOrEmail(String username, String email);

    boolean updatePasswordHash(Long userId, String passwordHash);
}
