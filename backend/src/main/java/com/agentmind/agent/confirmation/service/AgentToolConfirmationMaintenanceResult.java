package com.agentmind.agent.confirmation.service;

/**
 * 单次确认单维护结果。
 */
public record AgentToolConfirmationMaintenanceResult(int expiredCount, int recoveredCount) {
}
