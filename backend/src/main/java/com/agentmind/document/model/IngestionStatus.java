package com.agentmind.document.model;

/**
 * 文档摄取状态。
 *
 * <p>摄取任务从创建、解析、分块到向量化会经历多个阶段。当前先用通用状态，
 * 后续如果需要更细粒度状态，可在任务表中增加 stage 字段。</p>
 */
public enum IngestionStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELED
}
