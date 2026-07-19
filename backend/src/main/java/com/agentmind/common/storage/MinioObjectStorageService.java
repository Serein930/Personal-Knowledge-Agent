package com.agentmind.common.storage;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * MinIO 对象存储适配器。
 *
 * <p>应用层只保存对象键，不保存可伪造的公开地址。首次写入前幂等检查存储桶，
 * 多实例同时建桶时由 MinIO 的服务端幂等语义兜底。</p>
 */
@Service
@ConditionalOnProperty(prefix = "agentmind.storage", name = "type", havingValue = "minio")
public class MinioObjectStorageService implements ObjectStorageService {

    private final MinioClient client;
    private final String bucket;
    private final AtomicBoolean bucketReady = new AtomicBoolean();

    public MinioObjectStorageService(MinioStorageProperties properties) {
        this.client = MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
        this.bucket = properties.getBucket();
    }

    @Override
    public StoredObject store(String category, String originalName, InputStream inputStream,
            long size, String contentType) throws IOException {
        ensureBucket();
        String safeName = sanitize(originalName, "unnamed");
        String objectKey = sanitizeCategory(category) + "/" + UUID.randomUUID() + "-" + safeName;
        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(inputStream, size, -1)
                    .contentType(StringUtils.hasText(contentType) ? contentType : "application/octet-stream")
                    .build());
            return new StoredObject(objectKey, null, safeName, contentType, size);
        } catch (Exception exception) {
            throw new IOException("写入 MinIO 对象失败", exception);
        }
    }

    @Override
    public void delete(String storageKey) throws IOException {
        if (!StringUtils.hasText(storageKey)) {
            return;
        }
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(storageKey).build());
        } catch (Exception exception) {
            throw new IOException("删除 MinIO 对象失败", exception);
        }
    }

    private synchronized void ensureBucket() throws IOException {
        if (bucketReady.get()) {
            return;
        }
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
            bucketReady.set(true);
        } catch (Exception exception) {
            throw new IOException("初始化 MinIO 存储桶失败", exception);
        }
    }

    private String sanitizeCategory(String value) {
        if (!StringUtils.hasText(value)) {
            return "default";
        }
        return java.util.Arrays.stream(value.split("/"))
                .map(segment -> sanitize(segment, "default"))
                .collect(java.util.stream.Collectors.joining("/"));
    }

    private String sanitize(String value, String fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        String normalized = value.replace('\\', '/');
        normalized = normalized.substring(normalized.lastIndexOf('/') + 1);
        String safe = normalized.replaceAll("[^a-zA-Z0-9._-]", "-");
        return safe.isBlank() ? fallback : safe;
    }
}
