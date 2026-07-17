package com.agentmind.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * 使用真实 Keycloak 完成 OIDC 元数据发现、令牌签发、公钥下载和 JWT 验签。
 *
 * <p>测试默认关闭，避免常规 CI 拉取较大的身份服务镜像；生产验收流水线可显式开启。</p>
 */
@Testcontainers(disabledWithoutDocker = true)
@EnabledIfEnvironmentVariable(named = "AGENTMIND_RUN_OIDC_INTEGRATION", matches = "true")
class OidcJwtDecoderIntegrationTests {

    @Container
    private static final GenericContainer<?> KEYCLOAK = new GenericContainer<>(
            DockerImageName.parse("quay.io/keycloak/keycloak:26.0.7"))
            .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
            .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin-test-password")
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("oidc/agentmind-realm.json"),
                    "/opt/keycloak/data/import/agentmind-realm.json")
            .withCommand("start-dev", "--import-realm")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/realms/agentmind/.well-known/openid-configuration")
                    .forPort(8080).forStatusCode(200).withStartupTimeout(Duration.ofMinutes(3)));

    @Test
    void decoderShouldDiscoverIssuerAndVerifyRealToken() throws Exception {
        String issuer = "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080)
                + "/realms/agentmind";
        String token = requestAccessToken(issuer);
        AgentMindSecurityProperties properties = new AgentMindSecurityProperties();
        properties.setIssuerUri(issuer);
        properties.setAudience("agentmind-api");
        JwtDecoder decoder = new SecurityConfiguration().oidcJwtDecoder(properties);

        Jwt jwt = decoder.decode(token);

        assertThat(jwt.getIssuer().toString()).isEqualTo(issuer);
        assertThat(jwt.getClaimAsString("preferred_username")).isEqualTo("oidc-user");
        assertThat(jwt.getClaimAsString("uid")).isEqualTo("1001");
        assertThat(jwt.getAudience()).contains("agentmind-api");
    }

    private String requestAccessToken(String issuer) throws Exception {
        String form = "grant_type=password"
                + "&client_id=" + encode("agentmind-test")
                + "&username=" + encode("oidc-user")
                + "&password=" + encode("oidc-test-password");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(issuer + "/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode())
                .withFailMessage("Keycloak 令牌接口返回状态=%s，响应=%s", response.statusCode(), response.body())
                .isEqualTo(200);
        JsonNode json = new ObjectMapper().readTree(response.body());
        return json.path("access_token").asText();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
