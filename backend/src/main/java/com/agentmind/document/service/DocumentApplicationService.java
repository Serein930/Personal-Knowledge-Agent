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
import com.agentmind.document.repository.DocumentChunkRepository;
import com.agentmind.document.repository.InMemoryDocumentChunkRepository;
import com.agentmind.document.model.dto.DocumentChunkResponse;
import com.agentmind.document.model.dto.DocumentKeyPointResponse;
import com.agentmind.document.model.dto.DocumentSummaryResponse;
import com.agentmind.document.model.dto.FileDocumentUploadResponse;
import com.agentmind.document.model.dto.WebPageCaptureRequest;
import com.agentmind.document.model.dto.WebPageCaptureResponse;
import com.agentmind.document.parser.DocumentTextExtractionService;
import com.agentmind.document.parser.DocumentTextExtractionException;
import com.agentmind.document.parser.ExtractedDocumentText;
import com.agentmind.ingestion.model.IngestionTaskStatus;
import com.agentmind.ingestion.model.IngestionTaskType;
import com.agentmind.ingestion.model.IngestionTask;
import com.agentmind.ingestion.model.dto.IngestionTaskResponse;
import com.agentmind.ingestion.repository.IngestionTaskRepository;
import com.agentmind.ingestion.repository.InMemoryIngestionTaskRepository;
import com.agentmind.ingestion.web.HtmlFetchResult;
import com.agentmind.ingestion.web.HtmlFetchService;
import com.agentmind.ingestion.web.UrlSafetyValidator;
import com.agentmind.knowledge.service.KnowledgeIndexingService;
import com.agentmind.workspace.repository.KnowledgeWorkspaceRepository;
import com.agentmind.workspace.service.WorkspaceAccessService;
import com.agentmind.user.service.UserWorkspacePreferenceService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文档摄取用例的应用服务。
 *
 * <p>该服务让控制层保持轻量，并负责当前用例流程：校验入参、保存原始内容、提取文本、切分片段，
 * 更新任务和文档状态。所有正式摄取数据均通过仓储端口保存，开发环境可使用内存适配器，
 * 本地和生产环境则使用 PostgreSQL 适配器。</p>
 */
@Service
public class DocumentApplicationService {

    private static final Logger log = LoggerFactory.getLogger(DocumentApplicationService.class);
    private static final String DEFAULT_WORKSPACE_NAME = "Java Backend Learning";
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_KEY_POINTS = 8;

    private final FileUploadValidator fileUploadValidator;
    private final ObjectStorageService objectStorageService;
    private final UrlSafetyValidator urlSafetyValidator;
    private final HtmlFetchService htmlFetchService;
    private final DocumentTextExtractionService textExtractionService;
    private final TextChunker textChunker;
    private final KnowledgeIndexingService knowledgeIndexingService;
    private final DocumentMetadataRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final IngestionTaskRepository taskRepository;
    private final KnowledgeWorkspaceRepository workspaceRepository;
    private final WorkspaceAccessService workspaceAccessService;
    private final UserWorkspacePreferenceService preferenceService;

    @Value("${agentmind.demo.seed-data:true}")
    private boolean seedDemoData;

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
            DocumentChunkRepository chunkRepository,
            IngestionTaskRepository taskRepository,
            KnowledgeWorkspaceRepository workspaceRepository,
            WorkspaceAccessService workspaceAccessService,
            UserWorkspacePreferenceService preferenceService
    ) {
        this.fileUploadValidator = fileUploadValidator;
        this.objectStorageService = objectStorageService;
        this.urlSafetyValidator = urlSafetyValidator;
        this.htmlFetchService = htmlFetchService;
        this.textExtractionService = textExtractionService;
        this.textChunker = textChunker;
        this.knowledgeIndexingService = knowledgeIndexingService;
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.taskRepository = taskRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceAccessService = workspaceAccessService;
        this.preferenceService = preferenceService;
    }

    /** 为不启动 Spring 容器的摄取单元测试保留内存仓储构造方式。 */
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
        this(fileUploadValidator, objectStorageService, urlSafetyValidator, htmlFetchService,
                textExtractionService, textChunker, knowledgeIndexingService, documentRepository,
                new InMemoryDocumentChunkRepository(), new InMemoryIngestionTaskRepository(),
                workspaceRepository, workspaceAccessService, null);
    }

    /**
     * 仅在显式启用演示数据时建立固定检索片段；local 和 production 配置均关闭该开关。
     */
    @PostConstruct
    void seedMockDocuments() {
        if (!seedDemoData || documentRepository.findByWorkspaceIdAndId(1L, 1L).isEmpty()) {
            return;
        }
        List<DocumentChunk> chunks = List.of(
                new DocumentChunk("1-0", 1L, 0, "Thread Pool Basics",
                        "Thread pools reuse worker threads and reduce task creation overhead.", 0, 72),
                new DocumentChunk("1-1", 1L, 1, "Executor Tuning",
                        "Core size, max size and queue policy should match workload characteristics.", 73, 146)
        );
        chunkRepository.replaceDocumentChunks(1L, 1L, 1L, chunks);
        knowledgeIndexingService.indexChunks(1L, 1L, chunks);
    }

    /**
     * 创建文件摄取任务，并执行当前同步摄取流程。
     *
     * <p>该方法只读取一次上传字节，保存原始对象，为已支持格式提取文本，并通过仓储端口保存生成片段。
     * 后续可以把解析和切分迁移到异步执行器中，而不改变接口契约。</p>
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

        String displayTitle = resolveDocumentTitle(title, validation.safeFilename());
        List<String> normalizedTags = normalizeTags(tags);
        DocumentMetadata metadata = documentRepository.create(ownerUserId, workspaceId, displayTitle,
                validation.sourceType(), null, validation.safeFilename(), normalizedTags);
        Long documentId = metadata.id();
        IngestionTask task = taskRepository.create(ownerUserId, workspaceId, documentId,
                IngestionTaskType.FILE_UPLOAD, IngestionTaskStatus.RUNNING, 20, validation.safeFilename());
        Long taskId = task.id();

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
            chunkRepository.replaceDocumentChunks(ownerUserId, workspaceId, documentId, generatedChunks);
            indexChunks(ownerUserId, workspaceId, documentId, generatedChunks);

            documentRepository.markSucceeded(documentId, storedObject.storageKey(), validation.contentType(),
                    validation.size(), sha256(fileBytes), generatedChunks.size());
            taskRepository.update(taskId, IngestionTaskStatus.SUCCEEDED, 100, storedObject.storageKey(), null);
            return new FileDocumentUploadResponse(documentId, taskId, IngestionTaskStatus.SUCCEEDED);
        } catch (DocumentTextExtractionException exception) {
            markTaskFailed(documentId, taskId, displayTitle, validation.sourceType(), workspaceId,
                    normalizedTags, IngestionTaskType.FILE_UPLOAD, validation.safeFilename(),
                    exception.getMessage());
            throw new BusinessException(ErrorCode.BAD_REQUEST, exception.getMessage());
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

        HtmlFetchResult html;
        try {
            html = htmlFetchService.fetch(uri);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "网页抓取失败");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "网页抓取被中断");
        }
        byte[] htmlBytes = html.html().getBytes(StandardCharsets.UTF_8);
        String contentHash = sha256(htmlBytes);
        DocumentMetadata unchangedDocument = documentRepository
                .findLatestByWorkspaceIdAndSourceUri(workspaceId, uri.toString())
                .filter(document -> contentHash.equals(document.contentHash()))
                .orElse(null);
        if (unchangedDocument != null) {
            IngestionTask unchangedTask = taskRepository.create(ownerUserId, workspaceId, unchangedDocument.id(),
                    IngestionTaskType.WEB_PAGE_CAPTURE, IngestionTaskStatus.SUCCEEDED, 100, uri.toString());
            return new WebPageCaptureResponse(unchangedDocument.id(), unchangedTask.id(),
                    IngestionTaskStatus.SUCCEEDED);
        }

        String displayTitle = StringUtils.hasText(request.title()) ? request.title().trim() : uri.getHost();
        List<String> normalizedTags = normalizeTags(request.tags());
        DocumentMetadata metadata = documentRepository.create(ownerUserId, workspaceId, displayTitle,
                DocumentSourceType.WEB_PAGE, uri.toString(), null, normalizedTags);
        Long documentId = metadata.id();
        IngestionTask task = taskRepository.create(ownerUserId, workspaceId, documentId,
                IngestionTaskType.WEB_PAGE_CAPTURE, IngestionTaskStatus.RUNNING, 20, uri.toString());
        Long taskId = task.id();

        try {
            StoredObject storedObject = objectStorageService.store(
                    buildWorkspaceStorageCategory(workspaceId, "web-snapshots"),
                    documentId + ".html",
                    new ByteArrayInputStream(htmlBytes),
                    htmlBytes.length,
                    html.contentType()
            );

            List<DocumentChunk> generatedChunks = extractAndChunk(documentId, DocumentSourceType.WEB_PAGE,
                    uri.toString(), htmlBytes);
            chunkRepository.replaceDocumentChunks(ownerUserId, workspaceId, documentId, generatedChunks);
            indexChunks(ownerUserId, workspaceId, documentId, generatedChunks);

            documentRepository.markSucceeded(documentId, storedObject.storageKey(), html.contentType(),
                    htmlBytes.length, contentHash, generatedChunks.size());
            taskRepository.update(taskId, IngestionTaskStatus.SUCCEEDED, 100, storedObject.storageKey(), null);
            return new WebPageCaptureResponse(documentId, taskId, IngestionTaskStatus.SUCCEEDED);
        } catch (BusinessException exception) {
            markTaskFailed(documentId, taskId, displayTitle, DocumentSourceType.WEB_PAGE, workspaceId,
                    normalizedTags, IngestionTaskType.WEB_PAGE_CAPTURE, uri.toString(), exception.getMessage());
            return new WebPageCaptureResponse(documentId, taskId, IngestionTaskStatus.FAILED);
        } catch (IOException exception) {
            markTaskFailed(documentId, taskId, displayTitle, DocumentSourceType.WEB_PAGE, workspaceId,
                    normalizedTags, IngestionTaskType.WEB_PAGE_CAPTURE, uri.toString(), "网页摄取失败");
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
        return chunkRepository.findAllByDocumentId(documentId).stream()
                .map(this::toChunkResponse)
                .toList();
    }

    /**
     * 从摄取阶段已经生成的语义片段中归纳核心知识点，不要求用户先发起问答。
     */
    public List<DocumentKeyPointResponse> listDocumentKeyPoints(
            Long ownerUserId,
            Long workspaceId,
            Long documentId
    ) {
        DocumentMetadata document = requireDocument(ownerUserId, workspaceId, documentId, false);
        if (document.ingestionStatus() != IngestionStatus.SUCCEEDED) {
            return List.of();
        }
        List<DocumentKeyPointResponse> points = new ArrayList<>();
        java.util.Set<String> usedTitles = new java.util.LinkedHashSet<>();
        for (DocumentChunk chunk : chunkRepository.findAllByDocumentId(documentId)) {
            String summary = normalizeKeyPointSummary(chunk.content());
            if (!StringUtils.hasText(summary)) {
                continue;
            }
            String title = StringUtils.hasText(chunk.headingPath())
                    ? chunk.headingPath().trim() : "知识点 " + (points.size() + 1);
            if (!usedTitles.add(title) && points.size() >= 3) {
                continue;
            }
            points.add(new DocumentKeyPointResponse(points.size() + 1, title, summary, chunk.id()));
            if (points.size() >= MAX_KEY_POINTS) {
                break;
            }
        }
        return List.copyOf(points);
    }

    public DocumentSummaryResponse renameDocument(
            Long ownerUserId,
            Long workspaceId,
            Long documentId,
            String title
    ) {
        requireDocument(ownerUserId, workspaceId, documentId, true);
        String normalizedTitle = title == null ? "" : title.strip().replaceAll("\\s+", " ");
        if (!StringUtils.hasText(normalizedTitle)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文档标题不能为空");
        }
        DocumentMetadata updated = documentRepository.rename(workspaceId, documentId, normalizedTitle)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "文档不存在"));
        return toSummaryResponse(updated, workspaceName(workspaceId));
    }

    /** 删除文档元数据、片段、检索索引和原始存储对象。 */
    public void deleteDocument(Long ownerUserId, Long workspaceId, Long documentId) {
        DocumentMetadata document = requireDocument(ownerUserId, workspaceId, documentId, true);
        if (!documentRepository.softDelete(workspaceId, documentId)) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "文档状态已经变化，请刷新后重试");
        }
        chunkRepository.deleteAllByDocumentId(documentId);
        knowledgeIndexingService.deleteDocumentIndex(workspaceId, documentId);
        try {
            objectStorageService.delete(document.storageKey());
        } catch (IOException exception) {
            log.warn("文档已软删除，但原始对象清理失败：文档编号={}", documentId, exception);
        }
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
        return taskRepository.findByWorkspaceIdAndId(workspaceId, taskId)
                .map(this::toTaskResponse)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "摄取任务不存在"));
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

    private void indexChunks(Long ownerUserId, Long workspaceId, Long documentId, List<DocumentChunk> chunks) {
        String model = preferenceService == null ? null
                : preferenceService.get(ownerUserId, workspaceId).embeddingModel();
        knowledgeIndexingService.indexChunks(workspaceId, documentId, chunks, model);
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

    private DocumentMetadata requireDocument(
            Long ownerUserId,
            Long workspaceId,
            Long documentId,
            boolean writable
    ) {
        validateWorkspaceId(workspaceId);
        if (writable) {
            workspaceAccessService.requireWritable(ownerUserId, workspaceId);
        } else {
            workspaceAccessService.requireReadable(ownerUserId, workspaceId);
        }
        return documentRepository.findByWorkspaceIdAndId(workspaceId, documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "文档不存在"));
    }

    private String workspaceName(Long workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .map(com.agentmind.workspace.model.KnowledgeWorkspace::getName)
                .orElse("未知知识空间");
    }

    private String normalizeKeyPointSummary(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        int sentenceEnd = findSentenceEnd(normalized);
        String summary = sentenceEnd > 30 ? normalized.substring(0, sentenceEnd + 1) : normalized;
        return summary.length() <= 320 ? summary : summary.substring(0, 320) + "...";
    }

    private int findSentenceEnd(String content) {
        int result = -1;
        for (char separator : new char[]{'。', '！', '？', '.', '!', '?'}) {
            int index = content.indexOf(separator);
            if (index >= 0 && (result < 0 || index < result)) {
                result = index;
            }
        }
        return result;
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

    /** 计算稳定内容摘要，用于文件追踪和网页未变化去重。 */
    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 Java 运行环境不支持 SHA-256", exception);
        }
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
        chunkRepository.deleteAllByDocumentId(documentId);
        knowledgeIndexingService.deleteDocumentIndex(workspaceId, documentId);
        documentRepository.markFailed(documentId);
        taskRepository.update(taskId, IngestionTaskStatus.FAILED, 100, source, errorMessage);
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

    private IngestionTaskResponse toTaskResponse(IngestionTask task) {
        return new IngestionTaskResponse(task.id(), task.documentId(), task.taskType(), task.status(),
                task.progress(), task.source(), task.errorMessage(), task.createdAt(), task.updatedAt());
    }

    private DocumentSummaryResponse toSummaryResponse(DocumentMetadata document, String workspaceName) {
        return new DocumentSummaryResponse(document.id(), document.title(), document.sourceType(),
                document.workspaceId(), workspaceName, document.tags(), document.ingestionStatus(),
                document.chunkCount(), document.updatedAt());
    }
}
