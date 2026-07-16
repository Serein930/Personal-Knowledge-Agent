package com.agentmind.document.service;

import com.agentmind.common.exception.BusinessException;
import com.agentmind.common.exception.ErrorCode;
import com.agentmind.common.response.PageResponse;
import com.agentmind.common.storage.ObjectStorageService;
import com.agentmind.common.storage.StoredObject;
import com.agentmind.document.chunk.DocumentChunk;
import com.agentmind.document.chunk.TextChunker;
import com.agentmind.document.model.DocumentSourceType;
import com.agentmind.document.model.DocumentMetadata;
import com.agentmind.document.model.IngestionStatus;
import com.agentmind.document.repository.DocumentMetadataRepository;
import com.agentmind.document.model.dto.DocumentChunkResponse;
import com.agentmind.document.model.dto.DocumentSummaryResponse;
import com.agentmind.document.model.dto.FileDocumentUploadResponse;
import com.agentmind.document.model.dto.WebPageCaptureRequest;
import com.agentmind.document.model.dto.WebPageCaptureResponse;
import com.agentmind.document.parser.DocumentTextExtractionService;
import com.agentmind.document.parser.ExtractedDocumentText;
import com.agentmind.ingestion.model.IngestionTaskStatus;
import com.agentmind.ingestion.model.IngestionTaskType;
import com.agentmind.ingestion.model.dto.IngestionTaskResponse;
import com.agentmind.ingestion.web.HtmlFetchResult;
import com.agentmind.ingestion.web.HtmlFetchService;
import com.agentmind.ingestion.web.UrlSafetyValidator;
import com.agentmind.knowledge.service.KnowledgeIndexingService;
import com.agentmind.workspace.repository.KnowledgeWorkspaceRepository;
import com.agentmind.workspace.service.WorkspaceAccessService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档摄取用例的应用服务。
 *
 * <p>该服务让控制层保持轻量，并负责当前用例流程：校验入参、保存原始内容、提取文本、切分片段，
 * 更新任务和文档状态。文档元数据可按配置写入 PostgreSQL，摄取任务与解析片段暂时保留内存实现，
 * 后续可以在不改变控制层契约的情况下继续迁移。</p>
 */
@Service
public class DocumentApplicationService {

    private static final String DEFAULT_WORKSPACE_NAME = "Java Backend Learning";
    private static final int MAX_PAGE_SIZE = 100;

    private final FileUploadValidator fileUploadValidator;
    private final ObjectStorageService objectStorageService;
    private final UrlSafetyValidator urlSafetyValidator;
    private final HtmlFetchService htmlFetchService;
    private final DocumentTextExtractionService textExtractionService;
    private final TextChunker textChunker;
    private final KnowledgeIndexingService knowledgeIndexingService;
    private final AtomicLong taskIdGenerator = new AtomicLong(1000);
    private final Map<Long, IngestionTaskResponse> tasks = new ConcurrentHashMap<>();
    private final Map<Long, List<DocumentChunk>> chunksByDocumentId = new ConcurrentHashMap<>();
    private final DocumentMetadataRepository documentRepository;
    private final KnowledgeWorkspaceRepository workspaceRepository;
    private final WorkspaceAccessService workspaceAccessService;

    @Autowired
    public DocumentApplicationService(
            FileUploadValidator fileUploadValidator,
            ObjectStorageService objectStorageService,
            UrlSafetyValidator urlSafetyValidator,
            HtmlFetchService htmlFetchService,
            DocumentTextExtractionService textExtractionService,
            TextChunker textChunker,
            KnowledgeIndexingService knowledgeIndexingService,
            DocumentMetadataRepository documentRepository,
            KnowledgeWorkspaceRepository workspaceRepository,
            WorkspaceAccessService workspaceAccessService
    ) {
        this.fileUploadValidator = fileUploadValidator;
        this.objectStorageService = objectStorageService;
        this.urlSafetyValidator = urlSafetyValidator;
        this.htmlFetchService = htmlFetchService;
        this.textExtractionService = textExtractionService;
        this.textChunker = textChunker;
        this.knowledgeIndexingService = knowledgeIndexingService;
        this.documentRepository = documentRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceAccessService = workspaceAccessService;
        seedMockDocuments();
    }

    /**
     * 创建文件摄取任务，并执行当前同步摄取流程。
     *
     * <p>该方法只读取一次上传字节，保存原始对象，为已支持格式提取文本，并把生成片段写入内存片段仓库。
     * 后续阶段可以把解析和切分迁移到异步任务中，而不改变接口契约。</p>
     */
    public FileDocumentUploadResponse createFileUploadTask(
            Long workspaceId,
            MultipartFile file,
            String title,
            List<String> tags
    ) {
        return createFileUploadTask(1L, workspaceId, file, title, tags);
    }

    public FileDocumentUploadResponse createFileUploadTask(
            Long ownerUserId,
            Long workspaceId,
            MultipartFile file,
            String title,
            List<String> tags
    ) {
        validateWorkspaceId(workspaceId);
        workspaceAccessService.requireWritable(ownerUserId, workspaceId);
        FileValidationResult validation = fileUploadValidator.validate(file);

        Long taskId = taskIdGenerator.incrementAndGet();
        OffsetDateTime createdAt = OffsetDateTime.now();
        String displayTitle = resolveDocumentTitle(title, validation.safeFilename());
        List<String> normalizedTags = normalizeTags(tags);
        DocumentMetadata metadata = documentRepository.create(ownerUserId, workspaceId, displayTitle,
                validation.sourceType(), null, validation.safeFilename(), normalizedTags);
        Long documentId = metadata.id();

        putTask(taskId, documentId, IngestionTaskType.FILE_UPLOAD, IngestionTaskStatus.RUNNING,
                20, validation.safeFilename(), null, createdAt);

        try {
            byte[] fileBytes = file.getBytes();
            StoredObject storedObject = objectStorageService.store(
                    buildWorkspaceStorageCategory(workspaceId, "documents"),
                    validation.safeFilename(),
                    new ByteArrayInputStream(fileBytes),
                    validation.size(),
                    validation.contentType()
            );

            List<DocumentChunk> generatedChunks = extractAndChunk(documentId, validation.sourceType(),
                    validation.safeFilename(), fileBytes);
            chunksByDocumentId.put(documentId, generatedChunks);
            knowledgeIndexingService.indexChunks(workspaceId, documentId, generatedChunks);

            documentRepository.markSucceeded(documentId, storedObject.storageKey(), validation.contentType(),
                    validation.size(), generatedChunks.size());
            putTask(taskId, documentId, IngestionTaskType.FILE_UPLOAD, IngestionTaskStatus.SUCCEEDED,
                    100, storedObject.storageKey(), null, createdAt);
            return new FileDocumentUploadResponse(documentId, taskId, IngestionTaskStatus.SUCCEEDED);
        } catch (IOException exception) {
            markTaskFailed(documentId, taskId, displayTitle, validation.sourceType(), workspaceId,
                    normalizedTags, IngestionTaskType.FILE_UPLOAD, validation.safeFilename(),
                    "文件摄取失败");
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件摄取失败");
        }
    }

    /**
     * 创建网页摄取任务。
     *
     * <p>链接安全校验和网页抓取仍与解析流程分离。这样可以让服务端请求伪造防护、网络抓取、
     * 文本提取和切分策略分别测试。</p>
     */
    public WebPageCaptureResponse createWebPageCaptureTask(Long workspaceId, WebPageCaptureRequest request) {
        return createWebPageCaptureTask(1L, workspaceId, request);
    }

    public WebPageCaptureResponse createWebPageCaptureTask(
            Long ownerUserId,
            Long workspaceId,
            WebPageCaptureRequest request
    ) {
        validateWorkspaceId(workspaceId);
        workspaceAccessService.requireWritable(ownerUserId, workspaceId);
        URI uri = urlSafetyValidator.validatePublicHttpUrl(request.url());

        Long taskId = taskIdGenerator.incrementAndGet();
        OffsetDateTime createdAt = OffsetDateTime.now();
        String displayTitle = StringUtils.hasText(request.title()) ? request.title().trim() : uri.getHost();
        List<String> normalizedTags = normalizeTags(request.tags());
        DocumentMetadata metadata = documentRepository.create(ownerUserId, workspaceId, displayTitle,
                DocumentSourceType.WEB_PAGE, uri.toString(), null, normalizedTags);
        Long documentId = metadata.id();

        putTask(taskId, documentId, IngestionTaskType.WEB_PAGE_CAPTURE, IngestionTaskStatus.RUNNING,
                20, uri.toString(), null, createdAt);

        try {
            HtmlFetchResult html = htmlFetchService.fetch(uri);
            byte[] htmlBytes = html.html().getBytes(StandardCharsets.UTF_8);
            StoredObject storedObject = objectStorageService.store(
                    buildWorkspaceStorageCategory(workspaceId, "web-snapshots"),
                    documentId + ".html",
                    new ByteArrayInputStream(htmlBytes),
                    htmlBytes.length,
                    html.contentType()
            );

            List<DocumentChunk> generatedChunks = extractAndChunk(documentId, DocumentSourceType.WEB_PAGE,
                    uri.toString(), htmlBytes);
            chunksByDocumentId.put(documentId, generatedChunks);
            knowledgeIndexingService.indexChunks(workspaceId, documentId, generatedChunks);

            documentRepository.markSucceeded(documentId, storedObject.storageKey(), html.contentType(),
                    htmlBytes.length, generatedChunks.size());
            putTask(taskId, documentId, IngestionTaskType.WEB_PAGE_CAPTURE, IngestionTaskStatus.SUCCEEDED,
                    100, storedObject.storageKey(), null, createdAt);
            return new WebPageCaptureResponse(documentId, taskId, IngestionTaskStatus.SUCCEEDED);
        } catch (BusinessException exception) {
            markTaskFailed(documentId, taskId, displayTitle, DocumentSourceType.WEB_PAGE, workspaceId,
                    normalizedTags, IngestionTaskType.WEB_PAGE_CAPTURE, uri.toString(), exception.getMessage());
            return new WebPageCaptureResponse(documentId, taskId, IngestionTaskStatus.FAILED);
        } catch (IOException exception) {
            markTaskFailed(documentId, taskId, displayTitle, DocumentSourceType.WEB_PAGE, workspaceId,
                    normalizedTags, IngestionTaskType.WEB_PAGE_CAPTURE, uri.toString(), "网页摄取失败");
            return new WebPageCaptureResponse(documentId, taskId, IngestionTaskStatus.FAILED);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            markTaskFailed(documentId, taskId, displayTitle, DocumentSourceType.WEB_PAGE, workspaceId,
                    normalizedTags, IngestionTaskType.WEB_PAGE_CAPTURE, uri.toString(), "网页摄取被中断");
            return new WebPageCaptureResponse(documentId, taskId, IngestionTaskStatus.FAILED);
        }
    }

    public PageResponse<DocumentSummaryResponse> listDocuments(
            Long workspaceId,
            int page,
            int pageSize,
            String keyword,
            DocumentSourceType sourceType,
            IngestionStatus status,
            String tag
    ) {
        return listDocuments(1L, workspaceId, page, pageSize, keyword, sourceType, status, tag);
    }

    public PageResponse<DocumentSummaryResponse> listDocuments(
            Long ownerUserId,
            Long workspaceId,
            int page,
            int pageSize,
            String keyword,
            DocumentSourceType sourceType,
            IngestionStatus status,
            String tag
    ) {
        validateWorkspaceId(workspaceId);
        workspaceAccessService.requireReadable(ownerUserId, workspaceId);
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);

        String workspaceName = workspaceRepository.findById(workspaceId)
                .map(com.agentmind.workspace.model.KnowledgeWorkspace::getName)
                .orElse("未知知识空间");
        List<DocumentSummaryResponse> filtered = documentRepository.findAllByWorkspaceId(workspaceId).stream()
                .map(document -> toSummaryResponse(document, workspaceName))
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

    public List<DocumentChunkResponse> listDocumentChunks(Long workspaceId, Long documentId) {
        return listDocumentChunks(1L, workspaceId, documentId);
    }

    public List<DocumentChunkResponse> listDocumentChunks(Long ownerUserId, Long workspaceId, Long documentId) {
        validateWorkspaceId(workspaceId);
        workspaceAccessService.requireReadable(ownerUserId, workspaceId);
        if (documentRepository.findByWorkspaceIdAndId(workspaceId, documentId).isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "文档不存在");
        }
        return chunksByDocumentId.getOrDefault(documentId, List.of()).stream()
                .map(this::toChunkResponse)
                .toList();
    }

    public IngestionTaskResponse getTask(Long workspaceId, Long taskId) {
        return getTask(1L, workspaceId, taskId);
    }

    public IngestionTaskResponse getTask(Long ownerUserId, Long workspaceId, Long taskId) {
        validateWorkspaceId(workspaceId);
        workspaceAccessService.requireReadable(ownerUserId, workspaceId);
        if (taskId == null || taskId <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "任务ID必须为正数");
        }
        IngestionTaskResponse task = tasks.get(taskId);
        if (task == null || !workspaceId.equals(resolveTaskWorkspaceId(task))) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "摄取任务不存在");
        }
        return task;
    }

    private List<DocumentChunk> extractAndChunk(
            Long documentId,
            DocumentSourceType sourceType,
            String sourceName,
            byte[] content
    ) {
        ExtractedDocumentText extracted = textExtractionService.extract(sourceType, sourceName, content);
        return textChunker.chunk(documentId, sourceType, extracted.text());
    }

    private void seedMockDocuments() {
        OffsetDateTime now = OffsetDateTime.now();
        if (documentRepository.findByWorkspaceIdAndId(1L, 1L).isEmpty()) {
            return;
        }
        chunksByDocumentId.put(1L, List.of(
                new DocumentChunk("1-0", 1L, 0, "Thread Pool Basics",
                        "Thread pools reuse worker threads and reduce task creation overhead.", 0, 72),
                new DocumentChunk("1-1", 1L, 1, "Executor Tuning",
                        "Core size, max size and queue policy should match workload characteristics.", 73, 146)
        ));
        knowledgeIndexingService.indexChunks(1L, 1L, chunksByDocumentId.get(1L));
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
            throw new BusinessException(ErrorCode.BAD_REQUEST, "知识空间ID必须为正数");
        }
    }

    private String resolveDocumentTitle(String title, String fallbackFilename) {
        if (StringUtils.hasText(title)) {
            return title.trim();
        }
        return StringUtils.hasText(fallbackFilename) ? fallbackFilename : "未命名文档";
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
        chunksByDocumentId.remove(documentId);
        knowledgeIndexingService.deleteDocumentIndex(workspaceId, documentId);
        documentRepository.markFailed(documentId);
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

    private DocumentChunkResponse toChunkResponse(DocumentChunk chunk) {
        return new DocumentChunkResponse(
                chunk.id(),
                chunk.documentId(),
                chunk.sequence(),
                chunk.headingPath(),
                chunk.content(),
                chunk.charStart(),
                chunk.charEnd()
        );
    }

    private Long resolveTaskWorkspaceId(IngestionTaskResponse task) {
        return documentRepository.findById(task.documentId()).map(DocumentMetadata::workspaceId).orElse(null);
    }

    private DocumentSummaryResponse toSummaryResponse(DocumentMetadata document, String workspaceName) {
        return new DocumentSummaryResponse(document.id(), document.title(), document.sourceType(),
                document.workspaceId(), workspaceName, document.tags(), document.ingestionStatus(),
                document.chunkCount(), document.updatedAt());
    }
}
