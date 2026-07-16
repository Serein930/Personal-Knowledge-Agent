package com.agentmind.common.security;

import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agentmind.common.exception.GlobalExceptionHandler;
import com.agentmind.user.controller.CurrentUserController;
import com.agentmind.user.model.UserRole;
import com.agentmind.user.model.UserStatus;
import com.agentmind.user.model.dto.CurrentUserResponse;
import com.agentmind.user.service.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** 验证生产安全过滤器对无令牌请求和有效 JWT 请求的真实 HTTP 行为。 */
@WebMvcTest(CurrentUserController.class)
@Import({SecurityConfiguration.class, CurrentUserWebConfiguration.class,
        RestSecurityErrorHandler.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "agentmind.security.mode=local-jwt",
        "agentmind.security.jwt-secret=agentmind-test-secret-at-least-32-characters-long"
})
class SecurityFilterIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @Test
    void requestWithoutTokenShouldReturnUnifiedUnauthorizedResponse() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", equalTo("UNAUTHORIZED")));
    }

    @Test
    void validJwtShouldResolveCurrentUser() throws Exception {
        when(currentUserService.get(7L)).thenReturn(new CurrentUserResponse(
                7L, "serein", "Serein", "serein@example.com", UserRole.USER, UserStatus.ACTIVE));

        mockMvc.perform(get("/api/v1/users/me")
                        .with(jwt().jwt(token -> token.subject("7").claim("uid", 7L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", equalTo(7)))
                .andExpect(jsonPath("$.data.username", equalTo("serein")));
    }
}
