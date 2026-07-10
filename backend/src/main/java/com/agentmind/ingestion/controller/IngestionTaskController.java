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
 * 摄取任务状态查询接口。
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
            @PathVariable @Positive(message = "知识空间ID必须为正数") Long workspaceId,
            @PathVariable @Positive(message = "任务ID必须为正数") Long taskId
    ) {
        return ApiResponse.success(documentApplicationService.getTask(workspaceId, taskId));
    }
}
