package com.agentmind.common.storage;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 在 MinIO 模式启动早期拒绝空凭据，避免首次上传时才暴露配置错误。 */
@Component
public class MinioConfigurationValidator {

    private final String storageType;
    private final MinioStorageProperties properties;

    public MinioConfigurationValidator(
            @Value("${agentmind.storage.type:local}") String storageType,
            MinioStorageProperties properties
    ) {
        this.storageType = storageType;
        this.properties = properties;
    }

    @PostConstruct
    public void validate() {
        if (!"minio".equalsIgnoreCase(storageType)) {
            return;
        }
        if (!StringUtils.hasText(properties.getAccessKey()) || !StringUtils.hasText(properties.getSecretKey())) {
            throw new IllegalStateException("MinIO 模式必须配置访问密钥和秘密密钥");
        }
        if (!StringUtils.hasText(properties.getBucket())) {
            throw new IllegalStateException("MinIO 模式必须配置存储桶名称");
        }
    }
}
