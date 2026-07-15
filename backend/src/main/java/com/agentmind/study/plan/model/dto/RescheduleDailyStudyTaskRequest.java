package com.agentmind.study.plan.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** 重新安排任务请求。 */
public record RescheduleDailyStudyTaskRequest(
        @Min(value = 0, message = "预期版本不能为负数") long expectedVersion,
        @NotNull(message = "目标日期不能为空") LocalDate targetDate,
        @Size(max = 500, message = "说明不能超过500个字符") String comment
) {
}
