package com.agentmind.evaluation.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** 为评估任务提供与 Web 请求线程隔离的有界执行器。 */
@Configuration
public class RagEvaluationAsyncConfiguration {

    @Bean("ragEvaluationTaskExecutor")
    public TaskExecutor ragEvaluationTaskExecutor(RagEvaluationProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getExecutorCorePoolSize());
        executor.setMaxPoolSize(properties.getExecutorMaxPoolSize());
        executor.setQueueCapacity(properties.getExecutorQueueCapacity());
        executor.setThreadNamePrefix("rag-evaluation-");
        // 队列满时明确拒绝，由应用服务把已创建任务落为失败，避免 Web 请求退化为同步长任务。
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * 心跳使用独立调度线程，避免评估执行线程被模型调用占满时无法续租。
     */
    @Bean("ragEvaluationHeartbeatScheduler")
    public ThreadPoolTaskScheduler ragEvaluationHeartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("rag-evaluation-heartbeat-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(5);
        scheduler.initialize();
        return scheduler;
    }
}
