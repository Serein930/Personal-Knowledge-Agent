package com.agentmind.study.plan.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * 创建或调整每日学习目标请求。
 */
public record SaveDailyStudyPlanRequest(
        @NotNull(message = "计划日期不能为空")
        LocalDate planDate,

        @Min(value = 1, message = "每日复习目标不能小于1")
        @Max(value = 500, message = "每日复习目标不能大于500")
        int dailyReviewTarget,

        @Size(max = 10, message = "偏好主题不能超过10个")
        List<@Size(min = 1, max = 100, message = "主题长度必须在1到100之间") String> preferredTopics,

        @Size(max = 20, message = "来源文档不能超过20个")
        List<@Min(value = 1, message = "来源文档编号必须为正数") Long> sourceDocumentIds
) {

    /** 兼容既有只设置日期和数量的调用方。 */
    public SaveDailyStudyPlanRequest(LocalDate planDate, int dailyReviewTarget) {
        this(planDate, dailyReviewTarget, List.of(), List.of());
    }
}
