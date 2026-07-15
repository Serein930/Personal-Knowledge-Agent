package com.agentmind.study.maintenance.service;

import com.agentmind.study.maintenance.model.dto.StudyMaintenanceStatusResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;

/**
 * 最近一次学习维护运行的轻量观测器。
 *
 * <p>它不承担业务持久化，只用于快速判断定时任务是否卡住及处理了多少对象。正式接入
 * Micrometer 后，可以从同一组计数继续导出指标。</p>
 */
@Component
public class StudyMaintenanceMonitor {

    private boolean running;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
    private int processedScopes;
    private int optimizationJobs;
    private int compensatedTasks;
    private int rescheduledTasks;
    private int failureCount;
    private String lastError;

    public synchronized boolean tryStart() {
        if (running) {
            return false;
        }
        running = true;
        startedAt = OffsetDateTime.now();
        completedAt = null;
        processedScopes = 0;
        optimizationJobs = 0;
        compensatedTasks = 0;
        rescheduledTasks = 0;
        failureCount = 0;
        lastError = null;
        return true;
    }

    public synchronized void scopeProcessed() {
        processedScopes++;
    }

    public synchronized void optimizationCreated() {
        optimizationJobs++;
    }

    public synchronized void taskCompensated() {
        compensatedTasks++;
    }

    public synchronized void taskRescheduled() {
        rescheduledTasks++;
    }

    public synchronized void failed(RuntimeException exception) {
        failureCount++;
        lastError = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    public synchronized void complete() {
        running = false;
        completedAt = OffsetDateTime.now();
    }

    public synchronized StudyMaintenanceStatusResponse snapshot() {
        OffsetDateTime end = completedAt == null ? OffsetDateTime.now() : completedAt;
        long duration = startedAt == null ? 0 : Math.max(0, Duration.between(startedAt, end).toMillis());
        return new StudyMaintenanceStatusResponse(
                running, startedAt, completedAt, duration, processedScopes, optimizationJobs,
                compensatedTasks, rescheduledTasks, failureCount, lastError
        );
    }
}
