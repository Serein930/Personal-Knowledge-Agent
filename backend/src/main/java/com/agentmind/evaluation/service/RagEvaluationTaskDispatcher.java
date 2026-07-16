package com.agentmind.evaluation.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

/** 将已持久化的待执行任务投递到专用线程池。 */
@Component
public class RagEvaluationTaskDispatcher {

    private final TaskExecutor taskExecutor;
    private final RagEvaluationTaskRunner taskRunner;

    public RagEvaluationTaskDispatcher(
            @Qualifier("ragEvaluationTaskExecutor") TaskExecutor taskExecutor,
            RagEvaluationTaskRunner taskRunner
    ) {
        this.taskExecutor = taskExecutor;
        this.taskRunner = taskRunner;
    }

    public void dispatch(Long ownerUserId, Long workspaceId, Long jobId) {
        taskExecutor.execute(() -> taskRunner.run(ownerUserId, workspaceId, jobId));
    }
}
