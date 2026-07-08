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
 * HTTP API for ingestion task status queries.
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
            @PathVariable @Positive(message = "workspaceId must be positive") Long workspaceId,
            @PathVariable @Positive(message = "taskId must be positive") Long taskId
    ) {
        return ApiResponse.success(documentApplicationService.getTask(workspaceId, taskId));
    }
}
