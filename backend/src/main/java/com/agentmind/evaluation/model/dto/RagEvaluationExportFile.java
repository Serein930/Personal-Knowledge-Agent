package com.agentmind.evaluation.model.dto;

/** 控制器可直接下载的评估集文件。 */
public record RagEvaluationExportFile(
        String fileName,
        String contentType,
        byte[] content
) {
}
