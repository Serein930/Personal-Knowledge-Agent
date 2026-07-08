package com.agentmind.document.model.dto;

import com.agentmind.ingestion.model.IngestionTaskStatus;

/**
 * URL 采集接口响应 DTO。
 *
 * <p>与文件上传一致，URL 提交后返回的是异步任务信息，正文抓取、清洗、分块和向量化
 * 都在后续任务中完成。</p>
 */
public record WebPageCaptureResponse(
        Long documentId,
        Long taskId,
        IngestionTaskStatus status
) {
}
