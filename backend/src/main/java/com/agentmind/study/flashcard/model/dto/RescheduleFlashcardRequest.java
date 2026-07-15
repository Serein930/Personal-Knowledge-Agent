package com.agentmind.study.flashcard.model.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.OffsetDateTime;

/**
 * 手动重新排期请求。
 */
public record RescheduleFlashcardRequest(
        @NotNull(message = "新的到期时间不能为空")
        @FutureOrPresent(message = "新的到期时间不能早于当前时间")
        OffsetDateTime dueAt,

        @PositiveOrZero(message = "卡片版本号不能小于0")
        long expectedVersion
) {
}
