package com.agentmind.common.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 本地磁盘对象存储实现。
 *
 * <p>该适配器用于开发阶段和接口联调，会把上传文件与抓取到的网页快照存放在版本控制忽略的
 * 本地存储目录下。正式环境可切换到 MinIO 适配器，不需要修改文档摄取流程。</p>
 */
@Service
@ConditionalOnProperty(prefix = "agentmind.storage", name = "type", havingValue = "local", matchIfMissing = true)
public class LocalObjectStorageService implements ObjectStorageService {

    private final Path rootPath;

    public LocalObjectStorageService(@Value("${agentmind.storage.local-root:.agentmind-storage}") String localRoot) {
        this.rootPath = Path.of(localRoot).toAbsolutePath().normalize();
    }

    @Override
    public StoredObject store(String category, String originalName, InputStream inputStream, long size, String contentType)
            throws IOException {
        String safeCategory = sanitizePathSegment(category);
        String safeOriginalName = sanitizeFilename(originalName);
        String objectName = UUID.randomUUID() + "-" + safeOriginalName;
        Path targetDirectory = rootPath.resolve(safeCategory).normalize();
        Path targetPath = targetDirectory.resolve(objectName).normalize();

        if (!targetPath.startsWith(rootPath)) {
            throw new IOException("存储路径不合法");
        }

        Files.createDirectories(targetDirectory);
        Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);

        String storageKey = safeCategory + "/" + objectName;
        return new StoredObject(storageKey, targetPath, safeOriginalName, contentType, size);
    }

    @Override
    public void delete(String storageKey) throws IOException {
        if (!StringUtils.hasText(storageKey)) {
            return;
        }
        Path targetPath = rootPath.resolve(storageKey).normalize();
        if (!targetPath.startsWith(rootPath)) {
            throw new IOException("待删除对象路径不合法");
        }
        Files.deleteIfExists(targetPath);
    }

    private String sanitizePathSegment(String value) {
        if (!StringUtils.hasText(value)) {
            return "default";
        }
        return value.replaceAll("[^a-zA-Z0-9._-]", "-");
    }

    private String sanitizeFilename(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "unnamed";
        }
        String normalized = Path.of(filename).getFileName().toString();
        return normalized.replaceAll("[\\\\/:*?\"<>|]", "-");
    }
}
