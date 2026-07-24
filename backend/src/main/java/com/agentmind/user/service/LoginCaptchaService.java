package com.agentmind.user.service;

import com.agentmind.common.exception.BusinessException;
import com.agentmind.common.exception.ErrorCode;
import com.agentmind.user.model.dto.CaptchaChallengeResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 本地登录使用的一次性图形验证码服务。
 *
 * <p>服务端只保存验证码摘要，挑战在成功或失败校验后立即失效，避免同一验证码被重放。
 * SVG 中加入轻量噪声线与字符位置扰动，目标是拦截自动化撞库而不是替代限流机制。</p>
 */
@Service
public class LoginCaptchaService {

    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(3);
    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";

    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, CaptchaChallenge> challenges = new ConcurrentHashMap<>();
    private final StringRedisTemplate redisTemplate;

    public LoginCaptchaService() {
        this.redisTemplate = null;
    }

    @Autowired
    public LoginCaptchaService(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
    }

    public CaptchaChallengeResponse createChallenge() {
        removeExpiredChallenges();
        String challengeId = UUID.randomUUID().toString();
        String code = randomCode();
        OffsetDateTime expiresAt = OffsetDateTime.now().plus(CHALLENGE_TTL);
        CaptchaChallenge challenge = new CaptchaChallenge(hash(code), expiresAt);
        if (redisTemplate != null) {
            redisTemplate.opsForValue().set(redisKey(challengeId), challenge.codeHash(), CHALLENGE_TTL);
        } else {
            challenges.put(challengeId, challenge);
        }
        return new CaptchaChallengeResponse(challengeId, svgDataUri(code), expiresAt);
    }

    public void verify(String challengeId, String submittedCode) {
        if (challengeId == null || submittedCode == null) {
            throw invalidCaptcha();
        }
        String expectedHash;
        if (redisTemplate != null) {
            expectedHash = redisTemplate.opsForValue().getAndDelete(redisKey(challengeId));
        } else {
            CaptchaChallenge challenge = challenges.remove(challengeId);
            expectedHash = challenge == null || challenge.expiresAt().isBefore(OffsetDateTime.now())
                    ? null : challenge.codeHash();
        }
        if (expectedHash == null || !MessageDigest.isEqual(
                        expectedHash.getBytes(StandardCharsets.UTF_8),
                        hash(submittedCode.trim().toUpperCase(java.util.Locale.ROOT))
                                .getBytes(StandardCharsets.UTF_8))) {
            throw invalidCaptcha();
        }
    }

    private String randomCode() {
        StringBuilder result = new StringBuilder(4);
        for (int index = 0; index < 4; index++) {
            result.append(ALPHABET.charAt(secureRandom.nextInt(ALPHABET.length())));
        }
        return result.toString();
    }

    private String svgDataUri(String code) {
        int firstNoiseY = 18 + secureRandom.nextInt(24);
        int secondNoiseY = 20 + secureRandom.nextInt(22);
        StringBuilder characters = new StringBuilder();
        for (int index = 0; index < code.length(); index++) {
            int x = 24 + index * 30 + secureRandom.nextInt(4);
            int y = 40 + secureRandom.nextInt(8);
            int rotate = secureRandom.nextInt(17) - 8;
            characters.append("<text x=\"").append(x).append("\" y=\"").append(y)
                    .append("\" transform=\"rotate(").append(rotate).append(' ').append(x).append(' ').append(y)
                    .append(")\">").append(code.charAt(index)).append("</text>");
        }
        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" width="152" height="56" viewBox="0 0 152 56">
                  <rect width="152" height="56" rx="6" fill="#f4f7f6"/>
                  <path d="M8 %d C42 4, 94 54, 144 %d" stroke="#8fb8b3" stroke-width="1.2" fill="none"/>
                  <path d="M5 %d C52 51, 96 3, 148 %d" stroke="#b7c7c5" stroke-width="1" fill="none"/>
                  <g fill="#173b3a" font-family="Arial, sans-serif" font-size="25" font-weight="700">%s</g>
                </svg>
                """.formatted(firstNoiseY, secondNoiseY, secondNoiseY, firstNoiseY, characters);
        return "data:image/svg+xml;base64,"
                + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256", exception);
        }
    }

    private void removeExpiredChallenges() {
        if (redisTemplate != null) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        challenges.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private String redisKey(String challengeId) {
        return "agentmind:auth:captcha:" + challengeId;
    }

    private BusinessException invalidCaptcha() {
        return new BusinessException(ErrorCode.BAD_REQUEST, "验证码错误或已过期，请刷新后重试");
    }

    private record CaptchaChallenge(String codeHash, OffsetDateTime expiresAt) {
    }
}
