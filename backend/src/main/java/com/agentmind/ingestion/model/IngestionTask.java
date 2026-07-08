package com.agentmind.ingestion.model;

import java.time.OffsetDateTime;

/**
 * 摄取任务模型骨架。
 *
 * <p>摄取任务用于跟踪文件上传、网页采集、文本解析、分块和向量化流程。
 * Stage 2 只定义字段，Stage 3/Stage 4 再实现创建任务、状态流转和异步执行。</p>
 */
public class IngestionTask {

    private Long id;
    private Long ownerUserId;
    private Long workspaceId;
    private Long documentId;
    private IngestionTaskType taskType;
    private IngestionTaskStatus status;
    private int progress;
    private String source;
    private String errorMessage;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public Long getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(Long workspaceId) {
        this.workspaceId = workspaceId;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public IngestionTaskType getTaskType() {
        return taskType;
    }

    public void setTaskType(IngestionTaskType taskType) {
        this.taskType = taskType;
    }

    public IngestionTaskStatus getStatus() {
        return status;
    }

    public void setStatus(IngestionTaskStatus status) {
        this.status = status;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(OffsetDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public OffsetDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(OffsetDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }
}
