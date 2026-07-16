package com.agentmind.user.service;

import com.agentmind.common.exception.BusinessException;
import com.agentmind.common.exception.ErrorCode;
import com.agentmind.user.model.UserAccount;
import com.agentmind.user.model.dto.CurrentUserResponse;
import com.agentmind.user.repository.UserAccountRepository;
import org.springframework.stereotype.Service;

/** 查询当前账号安全视图，不返回密码摘要。 */
@Service
public class CurrentUserService {

    private final UserAccountRepository repository;

    public CurrentUserService(UserAccountRepository repository) {
        this.repository = repository;
    }

    public CurrentUserResponse get(Long userId) {
        UserAccount user = repository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在"));
        return new CurrentUserResponse(user.id(), user.username(), user.displayName(), user.email(),
                user.role(), user.status());
    }
}
