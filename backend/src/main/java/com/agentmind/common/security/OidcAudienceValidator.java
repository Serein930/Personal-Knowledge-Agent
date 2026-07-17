package com.agentmind.common.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/** 校验 OIDC 访问令牌是否明确面向 AgentMind 后端 API。 */
public class OidcAudienceValidator implements OAuth2TokenValidator<Jwt> {

    private final String requiredAudience;

    public OidcAudienceValidator(String requiredAudience) {
        if (requiredAudience == null || requiredAudience.isBlank()) {
            throw new IllegalArgumentException("OIDC 受众不能为空");
        }
        this.requiredAudience = requiredAudience;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        if (token.getAudience().contains(requiredAudience)) {
            return OAuth2TokenValidatorResult.success();
        }
        OAuth2Error error = new OAuth2Error(
                "invalid_token", "访问令牌未包含 AgentMind API 受众", null);
        return OAuth2TokenValidatorResult.failure(error);
    }
}
