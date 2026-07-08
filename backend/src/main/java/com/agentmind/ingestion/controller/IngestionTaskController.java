package com.agentmind.ingestion.controller;

import com.agentmind.common.response.ApiResponse;
import com.agentmind.document.service.DocumentApplicationService;
import com.agentmind.ingestion.model.dto.IngestionTaskResponse;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 摄取任务查询接口骨架。
 *
 * <p>当前阶段任务数据来自内存 mock service。后续异步摄取流程落地后，
 * 该接口会改为查询任务表或任务状态存储。</p>
 */
@Validated
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/ingestion-tasks")
public class IngestionTaskController {

    private final DocumentApplicationService documentApplicationService;

    public IngestionTaskController(DocumentApplicationService documentApplicationService) {
        this.documentApplicationService = documentApplicationService;
    }

    @GetMapping("/{taskId}")
    public ApiResponse<IngestionTaskResponse> getTask(
            @PathVariable @Positive(message = "workspaceId 必须为正数") Long workspaceId,
            @PathVariable @Positive(message = "taskId 必须为正数") Long taskId
    ) {
        return ApiResponse.success(documentApplicationService.getTask(workspaceId, taskId));
    }
}
