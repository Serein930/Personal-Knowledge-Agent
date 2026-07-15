package com.agentmind.study.plan.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** 任务执行反馈请求。 */
public record SubmitDailyStudyTaskFeedbackRequest(
        @Min(value = 0, message = "预期版本不能为负数") long expectedVersion,
        @Min(value = 1, message = "反馈评分最低为1")
        @Max(value = 5, message = "反馈评分最高为5") int score,
        @Size(max = 500, message = "反馈内容不能超过500个字符") String comment
) {
}
