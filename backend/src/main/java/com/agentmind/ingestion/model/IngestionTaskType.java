package com.agentmind.ingestion.model;

/**
 * 摄取任务类型。
 *
 * <p>任务类型决定后续异步处理流程：文件上传需要读取对象存储，
 * 网页采集需要抓取 URL，重建索引则基于已有文档重新生成 chunk 和向量。</p>
 */
public enum IngestionTaskType {
    FILE_UPLOAD,
    WEB_PAGE_CAPTURE,
    REINDEX
}
