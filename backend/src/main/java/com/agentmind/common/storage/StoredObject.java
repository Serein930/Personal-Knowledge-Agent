package com.agentmind.common.storage;

import java.nio.file.Path;

/**
 * 对象存储完成后返回的元数据。
 *
 * <p>绝对路径只用于本地开发调试。上层业务应使用存储键作为稳定引用，
 * 这样同一契约后续也能适配真实对象存储服务。</p>
 */
public record StoredObject(
        String storageKey,
        Path absolutePath,
        String originalName,
        String contentType,
        long size
) {
}
