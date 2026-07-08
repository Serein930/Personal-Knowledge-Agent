package com.agentmind.document.controller;

import com.agentmind.common.response.ApiResponse;
import com.agentmind.common.response.PageResponse;
import com.agentmind.document.model.DocumentSourceType;
import com.agentmind.document.model.IngestionStatus;
import com.agentmind.document.model.dto.DocumentSummaryResponse;
import com.agentmind.document.model.dto.FileDocumentUploadResponse;
import com.agentmind.document.model.dto.WebPageCaptureRequest;
import com.agentmind.document.model.dto.WebPageCaptureResponse;
import com.agentmind.document.service.DocumentApplicationService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档接口骨架。
 *
 * <p>当前 Controller 只暴露文件上传、网页采集和文档列表三个契约入口。
 * 业务处理委托给 DocumentApplicationService，避免 Controller 承载业务规则。</p>
 */
@Validated
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/documents")
public class DocumentController {

    private final DocumentApplicationService documentApplicationService;

    public DocumentController(DocumentApplicationService documentApplicationService) {
        this.documentApplicationService = documentApplicationService;
    }

    /**
     * 文件上传接口骨架。
     *
     * <p>该接口当前不会保存真实文件，只创建一个内存摄取任务，方便前端采集中心先完成联调。</p>
     */
    @PostMapping("/files")
    public ApiResponse<FileDocumentUploadResponse> uploadFile(
            @PathVariable @Positive(message = "workspaceId 必须为正数") Long workspaceId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) List<String> tags
    ) {
        return ApiResponse.success(documentApplicationService.createFileUploadTask(workspaceId, file, title, tags));
    }

    /**
     * URL 采集接口骨架。
     */
    @PostMapping("/web-pages")
    public ApiResponse<WebPageCaptureResponse> captureWebPage(
            @PathVariable @Positive(message = "workspaceId 必须为正数") Long workspaceId,
            @Valid @RequestBody WebPageCaptureRequest request
    ) {
        return ApiResponse.success(documentApplicationService.createWebPageCaptureTask(workspaceId, request));
    }

    /**
     * 文档列表接口骨架。
     *
     * <p>分页和筛选在内存中模拟。后续接入数据库时，接口参数和响应结构保持稳定。</p>
     */
    @GetMapping
    public ApiResponse<PageResponse<DocumentSummaryResponse>> listDocuments(
            @PathVariable @Positive(message = "workspaceId 必须为正数") Long workspaceId,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page 不能小于 1") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "pageSize 不能小于 1")
            @Max(value = 100, message = "pageSize 不能大于 100") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) DocumentSourceType sourceType,
            @RequestParam(required = false) IngestionStatus status,
            @RequestParam(required = false) String tag
    ) {
        return ApiResponse.success(documentApplicationService.listDocuments(
                workspaceId,
                page,
                pageSize,
                keyword,
                sourceType,
                status,
                tag
        ));
    }
}
