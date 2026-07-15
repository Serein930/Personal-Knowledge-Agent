package com.agentmind.study.session.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 创建复习会话请求。
 */
public record CreateReviewSessionRequest(
        @Min(value = 1, message = "会话卡片数量不能小于1")
        @Max(value = 100, message = "会话卡片数量不能大于100")
        int limit
) {
}
