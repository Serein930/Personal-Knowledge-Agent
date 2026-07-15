package com.agentmind.study.plan.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** 完成或跳过任务时使用的乐观锁请求。 */
public record UpdateDailyStudyTaskRequest(
        @Min(value = 0, message = "预期版本不能为负数") long expectedVersion,
        @Size(max = 500, message = "说明不能超过500个字符") String comment
) {
}
