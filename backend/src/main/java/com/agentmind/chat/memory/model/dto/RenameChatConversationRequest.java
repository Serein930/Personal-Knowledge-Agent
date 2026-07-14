package com.agentmind.chat.memory.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 会话重命名请求。
 *
 * <p>标题长度与自动生成标题保持一致，服务层还会去除首尾空白并合并连续空白字符。</p>
 */
public record RenameChatConversationRequest(
        @NotBlank(message = "会话标题不能为空")
        @Size(max = 60, message = "会话标题不能超过 60 个字符")
        String title
) {
}
