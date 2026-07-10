package com.agentmind.document.model.dto;

import com.agentmind.ingestion.model.IngestionTaskStatus;

/**
 * 链接采集接口响应数据传输对象。
 *
 * <p>与文件上传一致，链接提交后返回的是异步任务信息，正文抓取、清洗、分块和向量化
 * 都在后续任务中完成。</p>
 */
public record WebPageCaptureResponse(
        Long documentId,
        Long taskId,
        IngestionTaskStatus status
) {
}
