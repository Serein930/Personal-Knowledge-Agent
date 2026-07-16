package com.agentmind.common.storage;

import java.io.IOException;
import java.io.InputStream;

/**
 * 摄取服务使用的对象存储抽象。
 *
 * <p>应用层依赖该接口，而不是直接依赖具体存储产品。当前提供本地磁盘与 MinIO 两种适配器，
 * 可通过配置切换而不修改文档摄取流程。</p>
 */
public interface ObjectStorageService {

    StoredObject store(String category, String originalName, InputStream inputStream, long size, String contentType)
            throws IOException;
}
