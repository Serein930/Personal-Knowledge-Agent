package com.agentmind.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

/** 验证 OIDC 令牌受众不能被其他客户端的访问令牌绕过。 */
class OidcAudienceValidatorTests {

    @Test
    void requiredAudienceShouldPass() {
        OidcAudienceValidator validator = new OidcAudienceValidator("agentmind-api");

        assertThat(validator.validate(jwt(List.of("agentmind-api"))).hasErrors()).isFalse();
    }

    @Test
    void unrelatedAudienceShouldFail() {
        OidcAudienceValidator validator = new OidcAudienceValidator("agentmind-api");

        assertThat(validator.validate(jwt(List.of("another-service"))).hasErrors()).isTrue();
    }

    private Jwt jwt(List<String> audience) {
        Instant now = Instant.now();
        return new Jwt("token", now, now.plusSeconds(60), Map.of("alg", "none"),
                Map.of("sub", "1001", "aud", audience));
    }
}
