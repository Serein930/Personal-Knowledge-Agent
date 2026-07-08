package com.agentmind.document.service;

import com.agentmind.common.exception.BusinessException;
import com.agentmind.common.exception.ErrorCode;
import com.agentmind.common.response.PageResponse;
import com.agentmind.common.storage.ObjectStorageService;
import com.agentmind.common.storage.StoredObject;
import com.agentmind.document.model.DocumentSourceType;
import com.agentmind.document.model.IngestionStatus;
import com.agentmind.document.model.dto.DocumentSummaryResponse;
import com.agentmind.document.model.dto.FileDocumentUploadResponse;
import com.agentmind.document.model.dto.WebPageCaptureRequest;
import com.agentmind.document.model.dto.WebPageCaptureResponse;
import com.agentmind.ingestion.model.IngestionTaskStatus;
import com.agentmind.ingestion.model.IngestionTaskType;
import com.agentmind.ingestion.model.dto.IngestionTaskResponse;
import com.agentmind.ingestion.web.HtmlFetchResult;
import com.agentmind.ingestion.web.HtmlFetchService;
import com.agentmind.ingestion.web.UrlSafetyValidator;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档摄取应用服务。
 *
 * <p>该类负责把 Controller 收到的文件上传、URL 采集请求编排成明确的摄取流程。当前仍然使用内存
 * Map 保存文档和任务状态，但已经接入真实文件校验、本地对象存储、URL 安全校验和 HTML 抓取骨架。
 * 后续替换为数据库、消息队列和 MinIO 时，Controller 的 API 契约可以保持稳定。</p>
 */
@Service
public class DocumentApplicationService {

    private static final String DEFAULT_WORKSPACE_NAME = "Java 后端学习";
    private static final int MAX_PAGE_SIZE = 100;

    private final FileUploadValidator fileUploadValidator;
    private final ObjectStorageService objectStorageService;
    private final UrlSafetyValidator urlSafetyValidator;
    private final HtmlFetchService htmlFetchService;
    private final AtomicLong documentIdGenerator = new AtomicLong(100);
    private final AtomicLong taskIdGenerator = new AtomicLong(1000);
    private final Map<Long, DocumentSummaryResponse> documents = new ConcurrentHashMap<>();
    private final Map<Long, IngestionTaskResponse> tasks = new ConcurrentHashMap<>();

    public DocumentApplicationService(
            FileUploadValidator fileUploadValidator,
            ObjectStorageService objectStorageService,
            UrlSafetyValidator urlSafetyValidator,
            HtmlFetchService htmlFetchService
    ) {
        this.fileUploadValidator = fileUploadValidator;
        this.objectStorageService = objectStorageService;
        this.urlSafetyValidator = urlSafetyValidator;
        this.htmlFetchService = htmlFetchService;
        seedMockDocuments();
    }

    /**
     * 创建文件上传摄取任务。
     *
     * <p>Stage 4 先同步完成“校验 -> 保存原始文件 -> 更新任务状态”的最小闭环。真实生产项目中，
     * 保存完成后通常会把解析、分块、向量化投递到异步任务队列，因此这里的 chunkCount 仍然保持为 0。</p>
     */
    public FileDocumentUploadResponse createFileUploadTask(
            Long workspaceId,
            MultipartFile file,
            String title,
            List<String> tags
    ) {
        validateWorkspaceId(workspaceId);
        FileValidationResult validation = fileUploadValidator.validate(file);

        Long documentId = documentIdGenerator.incrementAndGet();
        Long taskId = taskIdGenerator.incrementAndGet();
        OffsetDateTime now = OffsetDateTime.now();
        String displayTitle = resolveDocumentTitle(title, validation.safeFilename());

        putDocument(documentId, displayTitle, validation.sourceType(), workspaceId, normalizeTags(tags),
                IngestionStatus.RUNNING, 0, now);
        putTask(taskId, documentId, IngestionTaskType.FILE_UPLOAD, IngestionTaskStatus.RUNNING,
                30, validation.safeFilename(), null, now);

        try {
            StoredObject storedObject = objectStorageService.store(
                    buildWorkspaceStorageCategory(workspaceId, "documents"),
                    validation.safeFilename(),
                    file.getInputStream(),
                    validation.size(),
                    validation.contentType()
            );

            putDocument(documentId, displayTitle, validation.sourceType(), workspaceId, normalizeTags(tags),
                    IngestionStatus.SUCCEEDED, 0, OffsetDateTime.now());
            putTask(taskId, documentId, IngestionTaskType.FILE_UPLOAD, IngestionTaskStatus.SUCCEEDED,
                    100, storedObject.storageKey(), null, now);
            return new FileDocumentUploadResponse(documentId, taskId, IngestionTaskStatus.SUCCEEDED);
        } catch (IOException exception) {
            markTaskFailed(documentId, taskId, displayTitle, validation.sourceType(), workspaceId,
                    normalizeTags(tags), IngestionTaskType.FILE_UPLOAD, validation.safeFilename(),
                    "文件保存失败，请稍后重试");
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件保存失败，请稍后重试");
        }
    }

    /**
     * 创建网页文章采集任务。
     *
     * <p>当前阶段会做 SSRF 基础防护并抓取原始 HTML 快照，再把快照保存到对象存储适配层。正文提取、
     * 去噪、重复检测和版本管理会在后续阶段继续基于保存下来的 HTML 结果推进。</p>
     */
    public WebPageCaptureResponse createWebPageCaptureTask(Long workspaceId, WebPageCaptureRequest request) {
        validateWorkspaceId(workspaceId);
        URI uri = urlSafetyValidator.validatePublicHttpUrl(request.url());

        Long documentId = documentIdGenerator.incrementAndGet();
        Long taskId = taskIdGenerator.incrementAndGet();
        OffsetDateTime now = OffsetDateTime.now();
        String displayTitle = StringUtils.hasText(request.title()) ? request.title().trim() : uri.getHost();
        List<String> normalizedTags = normalizeTags(request.tags());

        putDocument(documentId, displayTitle, DocumentSourceType.WEB_PAGE, workspaceId, normalizedTags,
                IngestionStatus.RUNNING, 0, now);
        putTask(taskId, documentId, IngestionTaskType.WEB_PAGE_CAPTURE, IngestionTaskStatus.RUNNING,
                30, uri.toString(), null, now);

        try {
            HtmlFetchResult html = htmlFetchService.fetch(uri);
            byte[] htmlBytes = html.html().getBytes(StandardCharsets.UTF_8);
            StoredObject storedObject = objectStorageService.store(
                    buildWorkspaceStorageCategory(workspaceId, "web-snapshots"),
                    documentId + ".html",
                    new ByteArrayInputStream(htmlBytes),
                    html.byteSize(),
                    html.contentType()
            );

            putDocument(documentId, displayTitle, DocumentSourceType.WEB_PAGE, workspaceId, normalizedTags,
                    IngestionStatus.SUCCEEDED, 0, OffsetDateTime.now());
            putTask(taskId, documentId, IngestionTaskType.WEB_PAGE_CAPTURE, IngestionTaskStatus.SUCCEEDED,
                    100, storedObject.storageKey(), null, now);
            return new WebPageCaptureResponse(documentId, taskId, IngestionTaskStatus.SUCCEEDED);
        } catch (BusinessException exception) {
            markTaskFailed(documentId, taskId, displayTitle, DocumentSourceType.WEB_PAGE, workspaceId,
                    normalizedTags, IngestionTaskType.WEB_PAGE_CAPTURE, uri.toString(), exception.getMessage());
            return new WebPageCaptureResponse(documentId, taskId, IngestionTaskStatus.FAILED);
        } catch (IOException exception) {
            markTaskFailed(documentId, taskId, displayTitle, DocumentSourceType.WEB_PAGE, workspaceId,
                    normalizedTags, IngestionTaskType.WEB_PAGE_CAPTURE, uri.toString(), "网页抓取失败，请稍后重试");
            return new WebPageCaptureResponse(documentId, taskId, IngestionTaskStatus.FAILED);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            markTaskFailed(documentId, taskId, displayTitle, DocumentSourceType.WEB_PAGE, workspaceId,
                    normalizedTags, IngestionTaskType.WEB_PAGE_CAPTURE, uri.toString(), "网页抓取任务被中断");
            return new WebPageCaptureResponse(documentId, taskId, IngestionTaskStatus.FAILED);
        }
    }

    /**
     * 查询文档列表。
     *
     * <p>这里仍然模拟分页、关键词、来源类型、状态和标签过滤。接入数据库后，该方法会替换为 Repository
     * 查询，但 Controller、DTO 和前端联调方式不需要发生大变化。</p>
     */
    public PageResponse<DocumentSummaryResponse> listDocuments(
            Long workspaceId,
            int page,
            int pageSize,
            String keyword,
            DocumentSourceType sourceType,
            IngestionStatus status,
            String tag
    ) {
        validateWorkspaceId(workspaceId);
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);

        List<DocumentSummaryResponse> filtered = documents.values().stream()
                .filter(document -> Objects.equals(document.workspaceId(), workspaceId))
                .filter(document -> matchesKeyword(document, keyword))
                .filter(document -> sourceType == null || document.sourceType() == sourceType)
                .filter(document -> status == null || document.ingestionStatus() == status)
                .filter(document -> !StringUtils.hasText(tag) || document.tags().contains(tag))
                .sorted(Comparator.comparing(DocumentSummaryResponse::updatedAt).reversed())
                .toList();

        int fromIndex = Math.min((safePage - 1) * safePageSize, filtered.size());
        int toIndex = Math.min(fromIndex + safePageSize, filtered.size());
        return new PageResponse<>(filtered.subList(fromIndex, toIndex), safePage, safePageSize, filtered.size());
    }

    /**
     * 查询单个摄取任务。
     */
    public IngestionTaskResponse getTask(Long workspaceId, Long taskId) {
        validateWorkspaceId(workspaceId);
        if (taskId == null || taskId <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "taskId 必须为正数");
        }
        IngestionTaskResponse task = tasks.get(taskId);
        if (task == null || !Objects.equals(resolveTaskWorkspaceId(task), workspaceId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "摄取任务不存在");
        }
        return task;
    }

    private void seedMockDocuments() {
        OffsetDateTime now = OffsetDateTime.now();
        documents.put(1L, new DocumentSummaryResponse(
                1L,
                "Java 并发编程笔记",
                DocumentSourceType.MARKDOWN,
                1L,
                DEFAULT_WORKSPACE_NAME,
                List.of("Java", "并发", "线程池"),
                IngestionStatus.SUCCEEDED,
                48,
                now.minusDays(1)
        ));
        documents.put(2L, new DocumentSummaryResponse(
                2L,
                "Spring AI 官方文档摘录",
                DocumentSourceType.WEB_PAGE,
                1L,
                "Agent 工程化",
                List.of("Spring AI", "RAG", "Tool Calling"),
                IngestionStatus.RUNNING,
                21,
                now
        ));
        tasks.put(1L, new IngestionTaskResponse(
                1L,
                2L,
                IngestionTaskType.WEB_PAGE_CAPTURE,
                IngestionTaskStatus.RUNNING,
                62,
                "https://docs.spring.io/spring-ai/reference/",
                null,
                now.minusMinutes(10),
                now
        ));
    }

    private void validateWorkspaceId(Long workspaceId) {
        if (workspaceId == null || workspaceId <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "workspaceId 必须为正数");
        }
    }

    private String resolveDocumentTitle(String title, String fallbackFilename) {
        if (StringUtils.hasText(title)) {
            return title.trim();
        }
        return StringUtils.hasText(fallbackFilename) ? fallbackFilename : "未命名文件";
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String tag : tags) {
            if (StringUtils.hasText(tag)) {
                normalized.add(tag.trim());
            }
        }
        return List.copyOf(normalized);
    }

    private String buildWorkspaceStorageCategory(Long workspaceId, String category) {
        return "workspace-" + workspaceId + "/" + category;
    }

    private void putDocument(
            Long documentId,
            String title,
            DocumentSourceType sourceType,
            Long workspaceId,
            List<String> tags,
            IngestionStatus ingestionStatus,
            int chunkCount,
            OffsetDateTime updatedAt
    ) {
        documents.put(documentId, new DocumentSummaryResponse(
                documentId,
                title,
                sourceType,
                workspaceId,
                DEFAULT_WORKSPACE_NAME,
                tags,
                ingestionStatus,
                chunkCount,
                updatedAt
        ));
    }

    private void putTask(
            Long taskId,
            Long documentId,
            IngestionTaskType taskType,
            IngestionTaskStatus status,
            int progress,
            String source,
            String errorMessage,
            OffsetDateTime createdAt
    ) {
        tasks.put(taskId, new IngestionTaskResponse(
                taskId,
                documentId,
                taskType,
                status,
                progress,
                source,
                errorMessage,
                createdAt,
                OffsetDateTime.now()
        ));
    }

    private void markTaskFailed(
            Long documentId,
            Long taskId,
            String title,
            DocumentSourceType sourceType,
            Long workspaceId,
            List<String> tags,
            IngestionTaskType taskType,
            String source,
            String errorMessage
    ) {
        putDocument(documentId, title, sourceType, workspaceId, tags, IngestionStatus.FAILED, 0, OffsetDateTime.now());
        putTask(taskId, documentId, taskType, IngestionTaskStatus.FAILED, 100, source, errorMessage, OffsetDateTime.now());
    }

    private boolean matchesKeyword(DocumentSummaryResponse document, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        String lowerKeyword = keyword.toLowerCase(Locale.ROOT);
        return document.title().toLowerCase(Locale.ROOT).contains(lowerKeyword)
                || document.tags().stream().anyMatch(tag -> tag.toLowerCase(Locale.ROOT).contains(lowerKeyword));
    }

    private Long resolveTaskWorkspaceId(IngestionTaskResponse task) {
        DocumentSummaryResponse document = documents.get(task.documentId());
        return document == null ? null : document.workspaceId();
    }
}
