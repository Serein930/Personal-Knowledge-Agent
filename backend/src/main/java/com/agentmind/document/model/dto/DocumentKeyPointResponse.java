package com.agentmind.document.model.dto;

/** 摄取完成后从文档片段自动归纳出的核心知识点。 */
public record DocumentKeyPointResponse(
        int sequence,
        String title,
        String summary,
        String chunkId
) {
}
