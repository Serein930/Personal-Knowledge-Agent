package com.agentmind.user.service;

import com.agentmind.common.exception.BusinessException;
import com.agentmind.common.exception.ErrorCode;
import com.agentmind.common.security.AgentMindSecurityProperties;
import com.agentmind.user.model.UserAccount;
import com.agentmind.user.model.UserStatus;
import com.agentmind.user.model.dto.AuthTokenResponse;
import com.agentmind.user.model.dto.LoginRequest;
import com.agentmind.user.model.dto.RegisterUserRequest;
import com.agentmind.user.repository.UserAccountRepository;
import com.agentmind.workspace.model.KnowledgeWorkspace;
import com.agentmind.workspace.repository.KnowledgeWorkspaceRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 本地账号注册、密码校验与 JWT 签发服务。 */
@Service
@ConditionalOnProperty(prefix = "agentmind.security", name = "mode", havingValue = "local-jwt")
public class LocalAuthenticationService {

    private final UserAccountRepository userRepository;
    private final KnowledgeWorkspaceRepository workspaceRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final AgentMindSecurityProperties properties;

    public LocalAuthenticationService(
            UserAccountRepository userRepository,
            KnowledgeWorkspaceRepository workspaceRepository,
            PasswordEncoder passwordEncoder,
            JwtEncoder jwtEncoder,
            AgentMindSecurityProperties properties
    ) {
        this.userRepository = userRepository;
        this.workspaceRepository = workspaceRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    @Transactional
    public AuthTokenResponse register(RegisterUserRequest request) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase(java.util.Locale.ROOT);
        if (userRepository.existsByUsernameOrEmail(username, email)) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "用户名或邮箱已经被使用");
        }
        try {
            UserAccount user = userRepository.create(username, request.displayName().trim(), email,
                    passwordEncoder.encode(request.password()));
            KnowledgeWorkspace workspace = workspaceRepository.createOwnedWorkspace(
                    user.id(), "我的知识空间", "注册时自动创建的个人知识空间");
            return issueToken(user, workspace.getId());
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "用户名或邮箱已经被使用");
        }
    }

    public AuthTokenResponse login(LoginRequest request) {
        UserAccount user = userRepository.findByUsername(request.username().trim())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误"));
        if (user.status() != UserStatus.ACTIVE || !passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        Long defaultWorkspaceId = workspaceRepository.findFirstOwnedBy(user.id())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND, "用户尚未配置可用知识空间"))
                .getId();
        return issueToken(user, defaultWorkspaceId);
    }

    /**
     * 为仍处于有效认证状态的本地用户轮换短期访问令牌。
     *
     * <p>刷新请求不接受用户编号，身份只能来自已经通过签名校验的认证主体。</p>
     */
    public AuthTokenResponse refresh(Long userId) {
        UserAccount user = userRepository.findById(userId)
                .filter(account -> account.status() == UserStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "当前用户不可用"));
        Long defaultWorkspaceId = workspaceRepository.findFirstOwnedBy(user.id())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND, "用户尚未配置可用知识空间"))
                .getId();
        return issueToken(user, defaultWorkspaceId);
    }

    private AuthTokenResponse issueToken(UserAccount user, Long defaultWorkspaceId) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(properties.getAccessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("agentmind")
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(user.id().toString())
                .claim("uid", user.id())
                .claim("username", user.username())
                .claim("roles", List.of(user.role().name()))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AuthTokenResponse("Bearer", token, OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC),
                user.id(), defaultWorkspaceId);
    }
}
