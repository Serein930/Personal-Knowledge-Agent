package com.agentmind.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** MinIO 连接、凭据和存储桶配置。 */
@Component
@ConfigurationProperties(prefix = "agentmind.storage.minio")
public class MinioStorageProperties {

    private String endpoint = "http://localhost:9000";
    private String accessKey = "";
    private String secretKey = "";
    private String bucket = "agentmind";

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }
}
