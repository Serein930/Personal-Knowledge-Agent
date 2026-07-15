package com.agentmind.study.maintenance.model.dto;

import java.time.OffsetDateTime;

/** 学习系统后台维护状态。 */
public record StudyMaintenanceStatusResponse(
        boolean running,
        OffsetDateTime lastStartedAt,
        OffsetDateTime lastCompletedAt,
        long lastDurationMillis,
        int processedScopes,
        int optimizationJobs,
        int compensatedTasks,
        int rescheduledTasks,
        int failureCount,
        String lastError
) {
}
