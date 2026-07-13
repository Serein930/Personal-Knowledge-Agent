package com.agentmind.chat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 检索增强生成流式任务执行配置。
 *
 * <p>流式回答不占用处理普通请求的容器线程，也不使用不可控的公共线程池。
 * 有界队列用于在本地阶段限制并发压力，应用关闭时会等待已接收任务完成。</p>
 */
@Configuration
public class RagStreamingConfiguration {

    @Bean(name = "ragStreamingTaskExecutor")
    public ThreadPoolTaskExecutor ragStreamingTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("rag-stream-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
