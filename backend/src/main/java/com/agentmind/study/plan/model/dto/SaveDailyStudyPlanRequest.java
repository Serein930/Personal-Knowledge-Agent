package com.agentmind.study.plan.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 创建或调整每日学习目标请求。
 */
public record SaveDailyStudyPlanRequest(
        @NotNull(message = "计划日期不能为空")
        LocalDate planDate,

        @Min(value = 1, message = "每日复习目标不能小于1")
        @Max(value = 500, message = "每日复习目标不能大于500")
        int dailyReviewTarget
) {
}
