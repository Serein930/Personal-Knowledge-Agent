package com.agentmind.ingestion.model;

/**
 * 摄取任务状态。
 *
 * <p>该状态用于前端采集中心展示任务进度，也用于后续异步任务重试和失败诊断。</p>
 */
public enum IngestionTaskStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELED
}
