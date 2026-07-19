package com.agentmind.document.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 修改知识资产展示标题的请求。 */
public record RenameDocumentRequest(
        @NotBlank(message = "文档标题不能为空")
        @Size(max = 200, message = "文档标题不能超过 200 个字符")
        String title
) {
}
