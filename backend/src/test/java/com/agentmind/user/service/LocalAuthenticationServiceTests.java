package com.agentmind.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentmind.common.exception.BusinessException;
import com.agentmind.common.security.AgentMindSecurityProperties;
import com.agentmind.common.security.SecurityConfiguration;
import com.agentmind.common.security.SecurityMode;
import com.agentmind.user.model.dto.LoginRequest;
import com.agentmind.user.model.dto.RegisterUserRequest;
import com.agentmind.user.repository.InMemoryUserAccountRepository;
import com.agentmind.workspace.repository.InMemoryKnowledgeWorkspaceRepository;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/** 验证注册事务模型、密码摘要和本地令牌声明。 */
class LocalAuthenticationServiceTests {

    @Test
    void shouldRegisterLoginAndIssueVerifiableJwt() {
        AgentMindSecurityProperties properties = properties();
        SecurityConfiguration configuration = new SecurityConfiguration();
        InMemoryUserAccountRepository users = new InMemoryUserAccountRepository();
        InMemoryKnowledgeWorkspaceRepository workspaces = new InMemoryKnowledgeWorkspaceRepository();
        LocalAuthenticationService service = new LocalAuthenticationService(users, workspaces,
                configuration.passwordEncoder(), configuration.localJwtEncoder(properties), properties);

        var registered = service.register(new RegisterUserRequest(
                "serein", "Serein", "serein@example.com", "a-strong-password-2026"));
        var loggedIn = service.login(new LoginRequest("serein", "a-strong-password-2026"));

        assertThat(loggedIn.userId()).isEqualTo(registered.userId());
        assertThat(loggedIn.defaultWorkspaceId()).isEqualTo(registered.defaultWorkspaceId());
        JwtDecoder decoder = configuration.localJwtDecoder(properties);
        Number uid = decoder.decode(loggedIn.accessToken()).getClaim("uid");
        assertThat(uid.longValue()).isEqualTo(registered.userId());
        assertThat(users.findByUsername("serein").orElseThrow().passwordHash())
                .doesNotContain("a-strong-password-2026");
    }

    @Test
    void wrongPasswordShouldReturnSameAuthenticationError() {
        AgentMindSecurityProperties properties = properties();
        SecurityConfiguration configuration = new SecurityConfiguration();
        LocalAuthenticationService service = new LocalAuthenticationService(
                new InMemoryUserAccountRepository(), new InMemoryKnowledgeWorkspaceRepository(),
                configuration.passwordEncoder(), configuration.localJwtEncoder(properties), properties);

        assertThatThrownBy(() -> service.login(new LoginRequest("missing", "wrong-password")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("用户名或密码错误");
    }

    private AgentMindSecurityProperties properties() {
        AgentMindSecurityProperties properties = new AgentMindSecurityProperties();
        properties.setMode(SecurityMode.LOCAL_JWT);
        properties.setJwtSecret("agentmind-test-secret-at-least-32-characters-long");
        properties.setAccessTokenTtl(Duration.ofMinutes(10));
        return properties;
    }
}
