package com.agentmind.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentmind.common.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/**
 * 验证上传大小边界及面向用户的错误信息，避免 HTTP 层与业务层配置调整后静默失配。
 */
class FileUploadValidatorTests {

    @Test
    void shouldAcceptFileAtConfiguredSizeBoundary() {
        FileUploadValidator validator = new FileUploadValidator(4L);
        MockMultipartFile file = new MockMultipartFile(
                "file", "note.txt", "text/plain", "test".getBytes(StandardCharsets.UTF_8)
        );

        FileValidationResult result = validator.validate(file);

        assertThat(result.size()).isEqualTo(4L);
    }

    @Test
    void shouldDescribeActualAndAllowedSizeWhenFileIsTooLarge() {
        FileUploadValidator validator = new FileUploadValidator(1024L * 1024L);
        MockMultipartFile file = new MockMultipartFile(
                "file", "large.txt", "text/plain", new byte[2 * 1024 * 1024]
        );

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("2.0 MiB")
                .hasMessageContaining("1.0 MiB");
    }
}
