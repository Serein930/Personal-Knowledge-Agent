package com.agentmind.agent.confirmation.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * 写工具确认令牌生成与校验服务。
 *
 * <p>令牌使用安全随机数生成，存储前经过摘要处理。比较摘要时使用固定时间比较，减少根据比较耗时
 * 推测令牌内容的风险。</p>
 */
@Component
public class AgentToolConfirmationTokenService {

    private static final int TOKEN_BYTES = 32;
    private final SecureRandom secureRandom = new SecureRandom();

    public IssuedConfirmationToken issue() {
        byte[] randomBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        return new IssuedConfirmationToken(rawToken, digest(rawToken));
    }

    public boolean matches(String rawToken, String expectedDigest) {
        if (rawToken == null || expectedDigest == null) {
            return false;
        }
        return MessageDigest.isEqual(
                digest(rawToken).getBytes(StandardCharsets.US_ASCII),
                expectedDigest.getBytes(StandardCharsets.US_ASCII)
        );
    }

    private String digest(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 Java 运行环境不支持 SHA-256", exception);
        }
    }

    public record IssuedConfirmationToken(String rawToken, String digest) {
    }
}
