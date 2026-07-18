package com.agentmind.user.model.dto;

import com.agentmind.user.model.CitationPolicy;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** 更新用户知识空间偏好请求。 */
public record UpdateUserWorkspacePreferenceRequest(
        @NotBlank(message = "聊天模型标识不能为空")
        @Size(max = 120, message = "聊天模型标识不能超过 120 个字符")
        String chatModel,

        @NotBlank(message = "Embedding 模型标识不能为空")
        @Size(max = 120, message = "Embedding 模型标识不能超过 120 个字符")
        String embeddingModel,

        @NotNull(message = "引用策略不能为空")
        CitationPolicy citationPolicy,

        @Min(value = 1, message = "默认召回数量至少为 1")
        @Max(value = 20, message = "默认召回数量不能大于 20")
        int defaultTopK,

        @PositiveOrZero(message = "偏好版本不能为负数")
        long expectedVersion
) {
}
