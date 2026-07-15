package com.agentmind.study.flashcard.fsrs.model.dto;

/**
 * 启动 FSRS 历史数据优化任务请求。
 *
 * @param applyResult 是否将推荐保持率立即写入用户参数档案
 */
public record StartFsrsOptimizationRequest(boolean applyResult) {
}
