package com.agentmind.common.storage;

import java.io.IOException;
import java.io.InputStream;

/**
 * 摄取服务使用的对象存储抽象。
 *
 * <p>应用层依赖该接口，而不是直接依赖本地磁盘或对象存储实现。这样当前本地开发适配器可以在后续
 * 平滑替换为真实对象存储。</p>
 */
public interface ObjectStorageService {

    StoredObject store(String category, String originalName, InputStream inputStream, long size, String contentType)
            throws IOException;
}
