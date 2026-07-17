package com.agentmind.common.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 身份认证、令牌签名和外部身份提供方配置。 */
@ConfigurationProperties(prefix = "agentmind.security")
public class AgentMindSecurityProperties {

    private SecurityMode mode = SecurityMode.DISABLED;
    private String jwtSecret = "";
    private String issuerUri = "";
    private String audience = "";
    private Duration accessTokenTtl = Duration.ofHours(2);

    public SecurityMode getMode() {
        return mode;
    }

    public void setMode(SecurityMode mode) {
        this.mode = mode;
    }

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public String getIssuerUri() {
        return issuerUri;
    }

    public void setIssuerUri(String issuerUri) {
        this.issuerUri = issuerUri;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public void setAccessTokenTtl(Duration accessTokenTtl) {
        this.accessTokenTtl = accessTokenTtl;
    }
}
