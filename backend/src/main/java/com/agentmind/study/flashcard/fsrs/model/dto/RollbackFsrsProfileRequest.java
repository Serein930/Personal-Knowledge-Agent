package com.agentmind.study.flashcard.fsrs.model.dto;

import jakarta.validation.constraints.Min;

/** 带乐观版本校验的 FSRS 参数回滚请求。 */
public record RollbackFsrsProfileRequest(
        @Min(value = 0, message = "目标版本不能为负数") long targetVersion,
        @Min(value = 0, message = "预期当前版本不能为负数") long expectedCurrentVersion
) {
}
