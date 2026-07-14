package com.agentmind.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 应用后台调度能力配置。
 *
 * <p>当前用于写工具确认单过期与异常执行恢复。后续摄取任务补偿、复习提醒等调度任务也应复用该入口。</p>
 */
@Configuration
@EnableScheduling
public class SchedulingConfiguration {
}
