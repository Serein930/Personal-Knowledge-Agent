package com.agentmind.study.flashcard.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 提交复习评分请求。
 *
 * <p>请求编号由客户端为一次用户操作生成并稳定重用。网络重试必须提交相同编号和评分，服务端据此避免
 * 重复推进卡片调度状态。</p>
 */
public record SubmitFlashcardReviewRequest(
        @NotBlank(message = "复习请求编号不能为空")
        @Size(max = 100, message = "复习请求编号长度不能超过100个字符")
        String requestId,

        @Min(value = 0, message = "复习评分不能小于0")
        @Max(value = 5, message = "复习评分不能大于5")
        int score
) {
}
