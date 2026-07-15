package com.agentmind.study.flashcard.model.dto;

import jakarta.validation.constraints.PositiveOrZero;

/**
 * 暂停或恢复卡片请求。
 *
 * @param expectedVersion 客户端最近一次读取到的版本号，用于阻止过期页面覆盖新调度结果
 */
public record ManageFlashcardRequest(
        @PositiveOrZero(message = "卡片版本号不能小于0")
        long expectedVersion
) {
}
