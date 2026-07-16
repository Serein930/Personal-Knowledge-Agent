package com.agentmind.evaluation.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

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
}
