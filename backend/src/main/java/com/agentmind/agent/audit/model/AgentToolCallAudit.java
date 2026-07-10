package com.agentmind.agent.audit.model;

import java.time.OffsetDateTime;

/**
 * 智能体工具调用审计模型骨架。
 *
 * <p>每一次智能体工具调用都应该保留审计记录，尤其是会写入笔记、复习卡片或学习计划的工具。
 * 请求载荷和响应摘要在后续实现时需要做敏感信息脱敏。</p>
 */
public class AgentToolCallAudit {

    private Long id;
    private Long ownerUserId;
    private Long workspaceId;
    private Long conversationId;
    private Long messageId;
    private String toolName;
    private AgentToolType toolType;
    private String requestPayload;
    private String responseSummary;
    private AgentToolCallStatus status;
    private String errorMessage;
    private long latencyMs;
    private OffsetDateTime createdAt;

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

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public Long getMessageId() {
        return messageId;
    }

    public void setMessageId(Long messageId) {
        this.messageId = messageId;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public AgentToolType getToolType() {
        return toolType;
    }

    public void setToolType(AgentToolType toolType) {
        this.toolType = toolType;
    }

    public String getRequestPayload() {
        return requestPayload;
    }

    public void setRequestPayload(String requestPayload) {
        this.requestPayload = requestPayload;
    }

    public String getResponseSummary() {
        return responseSummary;
    }

    public void setResponseSummary(String responseSummary) {
        this.responseSummary = responseSummary;
    }

    public AgentToolCallStatus getStatus() {
        return status;
    }

    public void setStatus(AgentToolCallStatus status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
