package com.agentmind.knowledge.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * 临时语义检索接口请求体。
 */
public record KnowledgeSearchRequest(
        @NotBlank(message = "查询内容不能为空")
        String query,

        @Min(value = 1, message = "topK 至少为 1")
        @Max(value = 20, message = "topK 不能大于 20")
        Integer topK
) {
}
