package com.agentmind.document.model.dto;

import com.agentmind.ingestion.model.IngestionTaskStatus;

/**
 * 文件上传接口响应 数据传输对象。
 *
 * <p>文件上传请求成功后，后端只代表“摄取任务已创建”，不代表文档已经解析完成。
 * 前端应使用任务编号查询异步摄取进度。</p>
 */
public record FileDocumentUploadResponse(
        Long documentId,
        Long taskId,
        IngestionTaskStatus status
) {
}
