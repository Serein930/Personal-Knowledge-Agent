package com.agentmind.workspace.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 创建个人知识空间请求。 */
public record CreateWorkspaceRequest(
        @NotBlank(message = "知识空间名称不能为空")
        @Size(max = 120, message = "知识空间名称不能超过120个字符")
        String name,
        @Size(max = 1000, message = "知识空间描述不能超过1000个字符")
        String description
) {
}
