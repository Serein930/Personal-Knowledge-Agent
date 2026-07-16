package com.agentmind.common.storage;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** 验证 MinIO 凭据不会以空值进入运行期。 */
class MinioConfigurationValidatorTests {

    @Test
    void minioModeShouldRejectEmptyCredentials() {
        assertThatThrownBy(() -> new MinioConfigurationValidator("minio", new MinioStorageProperties()).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("访问密钥");
    }

    @Test
    void localModeShouldNotRequireMinioCredentials() {
        assertThatCode(() -> new MinioConfigurationValidator("local", new MinioStorageProperties()).validate())
                .doesNotThrowAnyException();
    }
}
