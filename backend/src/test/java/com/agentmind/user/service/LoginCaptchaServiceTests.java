package com.agentmind.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentmind.common.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class LoginCaptchaServiceTests {

    private static final Pattern CHARACTER_PATTERN = Pattern.compile("<text[^>]*>([^<])</text>");

    @Test
    void challengeShouldBeOneTimeAndRejectReplay() {
        LoginCaptchaService service = new LoginCaptchaService();
        var challenge = service.createChallenge();
        String code = extractCode(challenge.imageDataUri());

        service.verify(challenge.challengeId(), code);

        assertThatThrownBy(() -> service.verify(challenge.challengeId(), code))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("验证码错误或已过期");
    }

    @Test
    void challengeShouldNotExposePlainCodeOutsideImage() {
        LoginCaptchaService service = new LoginCaptchaService();
        var challenge = service.createChallenge();

        assertThat(challenge.challengeId()).doesNotContain(extractCode(challenge.imageDataUri()));
        assertThat(challenge.expiresAt()).isAfter(java.time.OffsetDateTime.now());
    }

    private String extractCode(String dataUri) {
        String encoded = dataUri.substring(dataUri.indexOf(',') + 1);
        String svg = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        Matcher matcher = CHARACTER_PATTERN.matcher(svg);
        StringBuilder code = new StringBuilder();
        while (matcher.find()) {
            code.append(matcher.group(1));
        }
        return code.toString();
    }
}
