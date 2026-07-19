package com.agentmind.document.controller;

import com.agentmind.common.response.ApiResponse;
import com.agentmind.common.response.PageResponse;
import com.agentmind.common.security.CurrentUserId;
import com.agentmind.document.model.DocumentSourceType;
import com.agentmind.document.model.IngestionStatus;
import com.agentmind.document.model.dto.DocumentChunkResponse;
import com.agentmind.document.model.dto.DocumentKeyPointResponse;
import com.agentmind.document.model.dto.RenameDocumentRequest;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档摄取与文档查询接口。
 *
 * <p>该控制层只负责请求响应映射和参数校验。所有摄取、解析和切分决策都委托给文档应用服务。</p>
 */
@Validated
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/documents")
public class DocumentController {

    private final DocumentApplicationService documentApplicationService;

    public DocumentController(DocumentApplicationService documentApplicationService) {
        this.documentApplicationService = documentApplicationService;
    }

    @PostMapping("/files")
    public ApiResponse<FileDocumentUploadResponse> uploadFile(
            @CurrentUserId Long ownerUserId,
            @PathVariable @Positive(message = "知识空间ID必须为正数") Long workspaceId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) List<String> tags
    ) {
        return ApiResponse.success(documentApplicationService.createFileUploadTask(
                ownerUserId, workspaceId, file, title, tags));
    }

    @PostMapping("/web-pages")
    public ApiResponse<WebPageCaptureResponse> captureWebPage(
            @CurrentUserId Long ownerUserId,
            @PathVariable @Positive(message = "知识空间ID必须为正数") Long workspaceId,
            @Valid @RequestBody WebPageCaptureRequest request
    ) {
        return ApiResponse.success(documentApplicationService.createWebPageCaptureTask(
                ownerUserId, workspaceId, request));
    }

    @GetMapping
    public ApiResponse<PageResponse<DocumentSummaryResponse>> listDocuments(
            @CurrentUserId Long ownerUserId,
            @PathVariable @Positive(message = "知识空间ID必须为正数") Long workspaceId,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码必须大于 0") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "每页数量必须大于 0")
            @Max(value = 100, message = "每页数量不能大于 100") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) DocumentSourceType sourceType,
            @RequestParam(required = false) IngestionStatus status,
            @RequestParam(required = false) String tag
    ) {
        return ApiResponse.success(documentApplicationService.listDocuments(
                ownerUserId,
                workspaceId,
                page,
                pageSize,
                keyword,
                sourceType,
                status,
                tag
        ));
    }

    @GetMapping("/{documentId}/chunks")
    public ApiResponse<List<DocumentChunkResponse>> listDocumentChunks(
            @CurrentUserId Long ownerUserId,
            @PathVariable @Positive(message = "知识空间ID必须为正数") Long workspaceId,
            @PathVariable @Positive(message = "文档ID必须为正数") Long documentId
    ) {
        return ApiResponse.success(documentApplicationService.listDocumentChunks(ownerUserId, workspaceId, documentId));
    }

    @GetMapping("/{documentId}/key-points")
    public ApiResponse<List<DocumentKeyPointResponse>> listDocumentKeyPoints(
            @CurrentUserId Long ownerUserId,
            @PathVariable @Positive Long workspaceId,
            @PathVariable @Positive Long documentId
    ) {
        return ApiResponse.success(documentApplicationService.listDocumentKeyPoints(
                ownerUserId, workspaceId, documentId));
    }

    @PatchMapping("/{documentId}")
    public ApiResponse<DocumentSummaryResponse> renameDocument(
            @CurrentUserId Long ownerUserId,
            @PathVariable @Positive Long workspaceId,
            @PathVariable @Positive Long documentId,
            @Valid @RequestBody RenameDocumentRequest request
    ) {
        return ApiResponse.success(documentApplicationService.renameDocument(
                ownerUserId, workspaceId, documentId, request.title()));
    }

    @DeleteMapping("/{documentId}")
    public ApiResponse<Void> deleteDocument(
            @CurrentUserId Long ownerUserId,
            @PathVariable @Positive Long workspaceId,
            @PathVariable @Positive Long documentId
    ) {
        documentApplicationService.deleteDocument(ownerUserId, workspaceId, documentId);
        return ApiResponse.success("知识资产已删除", null);
    }
}
