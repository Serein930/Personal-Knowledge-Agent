package com.agentmind.study.flashcard.service;

import java.util.function.Supplier;

/**
 * 卡片调度更新与复习记录写入的事务边界。
 */
public interface FlashcardReviewTransactionBoundary {

    <T> T execute(Supplier<T> action);
}
