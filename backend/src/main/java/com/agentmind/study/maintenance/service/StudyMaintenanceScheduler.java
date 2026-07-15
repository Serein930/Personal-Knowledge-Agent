package com.agentmind.study.maintenance.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 配置显式开启后，周期执行学习画像刷新、FSRS 拟合和任务补偿。 */
@Component
@ConditionalOnProperty(prefix = "agentmind.study.maintenance", name = "enabled", havingValue = "true")
public class StudyMaintenanceScheduler {

    private final StudyMaintenanceApplicationService service;

    public StudyMaintenanceScheduler(StudyMaintenanceApplicationService service) {
        this.service = service;
    }

    @Scheduled(
            fixedDelayString = "${agentmind.study.maintenance.fixed-delay-millis:21600000}",
            initialDelayString = "${agentmind.study.maintenance.initial-delay-millis:60000}"
    )
    public void maintain() {
        service.runScheduled();
    }
}
