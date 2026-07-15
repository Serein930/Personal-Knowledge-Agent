package com.agentmind.evaluation.controller;

import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.common.response.ApiResponse;
import com.agentmind.common.response.PageResponse;
import com.agentmind.evaluation.model.dto.CreateRagEvaluationDatasetRequest;
import com.agentmind.evaluation.model.dto.CreateRagEvaluationDatasetVersionRequest;
import com.agentmind.evaluation.model.dto.RagEvaluationComparisonResponse;
import com.agentmind.evaluation.model.dto.RagEvaluationDashboardResponse;
import com.agentmind.evaluation.model.dto.RagEvaluationDatasetResponse;
import com.agentmind.evaluation.model.dto.RagEvaluationDatasetVersionResponse;
import com.agentmind.evaluation.model.dto.RagEvaluationJobResponse;
import com.agentmind.evaluation.model.dto.StartRagEvaluationJobRequest;
import com.agentmind.evaluation.service.RagEvaluationDatasetService;
import com.agentmind.evaluation.service.RagEvaluationJobService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 检索增强生成固定评估接口。
 *
 * <p>接口只接收和返回传输对象，知识空间授权、版本规则、任务状态流转与指标计算全部由应用层负责。</p>
 */
@Validated
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/evaluations")
public class RagEvaluationController {

    private final RagEvaluationDatasetService datasetService;
    private final RagEvaluationJobService jobService;

    public RagEvaluationController(
            RagEvaluationDatasetService datasetService,
            RagEvaluationJobService jobService
    ) {
        this.datasetService = datasetService;
        this.jobService = jobService;
    }

    @PostMapping("/datasets")
    public ApiResponse<RagEvaluationDatasetVersionResponse> createDataset(
            @PathVariable @Positive(message = "知识空间编号必须为正数") Long workspaceId,
            @RequestHeader(name = "X-Demo-User-Id", defaultValue = "1")
            @Positive(message = "演示用户编号必须为正数") Long ownerUserId,
            @Valid @RequestBody CreateRagEvaluationDatasetRequest request
    ) {
        return ApiResponse.success(datasetService.create(context(ownerUserId, workspaceId), request));
    }

    @GetMapping("/datasets")
    public ApiResponse<PageResponse<RagEvaluationDatasetResponse>> listDatasets(
            @PathVariable @Positive(message = "知识空间编号必须为正数") Long workspaceId,
            @RequestHeader(name = "X-Demo-User-Id", defaultValue = "1")
            @Positive(message = "演示用户编号必须为正数") Long ownerUserId,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码不能小于1") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "分页大小不能小于1")
            @Max(value = 100, message = "分页大小不能超过100") int pageSize
    ) {
        return ApiResponse.success(datasetService.list(context(ownerUserId, workspaceId), page, pageSize));
    }

    @PostMapping("/datasets/{datasetId}/versions")
    public ApiResponse<RagEvaluationDatasetVersionResponse> createVersion(
            @PathVariable @Positive(message = "知识空间编号必须为正数") Long workspaceId,
            @PathVariable @Positive(message = "评估集编号必须为正数") Long datasetId,
            @RequestHeader(name = "X-Demo-User-Id", defaultValue = "1")
            @Positive(message = "演示用户编号必须为正数") Long ownerUserId,
            @Valid @RequestBody CreateRagEvaluationDatasetVersionRequest request
    ) {
        return ApiResponse.success(datasetService.createVersion(
                context(ownerUserId, workspaceId), datasetId, request
        ));
    }

    @GetMapping("/datasets/{datasetId}/versions")
    public ApiResponse<List<RagEvaluationDatasetVersionResponse>> listVersions(
            @PathVariable @Positive(message = "知识空间编号必须为正数") Long workspaceId,
            @PathVariable @Positive(message = "评估集编号必须为正数") Long datasetId,
            @RequestHeader(name = "X-Demo-User-Id", defaultValue = "1")
            @Positive(message = "演示用户编号必须为正数") Long ownerUserId
    ) {
        return ApiResponse.success(datasetService.listVersions(context(ownerUserId, workspaceId), datasetId));
    }

    @GetMapping("/datasets/{datasetId}/versions/{version}")
    public ApiResponse<RagEvaluationDatasetVersionResponse> getVersion(
            @PathVariable @Positive(message = "知识空间编号必须为正数") Long workspaceId,
            @PathVariable @Positive(message = "评估集编号必须为正数") Long datasetId,
            @PathVariable @Positive(message = "评估集版本必须为正数") int version,
            @RequestHeader(name = "X-Demo-User-Id", defaultValue = "1")
            @Positive(message = "演示用户编号必须为正数") Long ownerUserId
    ) {
        return ApiResponse.success(datasetService.getVersion(
                context(ownerUserId, workspaceId), datasetId, version
        ));
    }

    @PostMapping("/jobs")
    public ApiResponse<RagEvaluationJobResponse> startJob(
            @PathVariable @Positive(message = "知识空间编号必须为正数") Long workspaceId,
            @RequestHeader(name = "X-Demo-User-Id", defaultValue = "1")
            @Positive(message = "演示用户编号必须为正数") Long ownerUserId,
            @Valid @RequestBody StartRagEvaluationJobRequest request
    ) {
        return ApiResponse.success(jobService.start(context(ownerUserId, workspaceId), request));
    }

    @GetMapping("/jobs")
    public ApiResponse<PageResponse<RagEvaluationJobResponse>> listJobs(
            @PathVariable @Positive(message = "知识空间编号必须为正数") Long workspaceId,
            @RequestHeader(name = "X-Demo-User-Id", defaultValue = "1")
            @Positive(message = "演示用户编号必须为正数") Long ownerUserId,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码不能小于1") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "分页大小不能小于1")
            @Max(value = 100, message = "分页大小不能超过100") int pageSize
    ) {
        return ApiResponse.success(jobService.list(context(ownerUserId, workspaceId), page, pageSize));
    }

    @GetMapping("/jobs/{jobId}")
    public ApiResponse<RagEvaluationJobResponse> getJob(
            @PathVariable @Positive(message = "知识空间编号必须为正数") Long workspaceId,
            @PathVariable @Positive(message = "评估任务编号必须为正数") Long jobId,
            @RequestHeader(name = "X-Demo-User-Id", defaultValue = "1")
            @Positive(message = "演示用户编号必须为正数") Long ownerUserId
    ) {
        return ApiResponse.success(jobService.get(context(ownerUserId, workspaceId), jobId));
    }

    @GetMapping("/jobs/{jobId}/comparison")
    public ApiResponse<RagEvaluationComparisonResponse> comparison(
            @PathVariable @Positive(message = "知识空间编号必须为正数") Long workspaceId,
            @PathVariable @Positive(message = "评估任务编号必须为正数") Long jobId,
            @RequestHeader(name = "X-Demo-User-Id", defaultValue = "1")
            @Positive(message = "演示用户编号必须为正数") Long ownerUserId,
            @RequestParam(required = false) @Positive(message = "基线任务编号必须为正数") Long baselineJobId
    ) {
        return ApiResponse.success(jobService.compare(
                context(ownerUserId, workspaceId), jobId, baselineJobId
        ));
    }

    @GetMapping("/dashboard")
    public ApiResponse<RagEvaluationDashboardResponse> dashboard(
            @PathVariable @Positive(message = "知识空间编号必须为正数") Long workspaceId,
            @RequestHeader(name = "X-Demo-User-Id", defaultValue = "1")
            @Positive(message = "演示用户编号必须为正数") Long ownerUserId
    ) {
        return ApiResponse.success(jobService.dashboard(context(ownerUserId, workspaceId)));
    }

    private AgentToolExecutionContext context(Long ownerUserId, Long workspaceId) {
        return new AgentToolExecutionContext(ownerUserId, workspaceId, null);
    }
}
