package com.agentmind.document.service;

import com.agentmind.common.exception.BusinessException;
import com.agentmind.common.exception.ErrorCode;
import com.agentmind.common.response.PageResponse;
import com.agentmind.common.storage.ObjectStorageService;
import com.agentmind.common.storage.StoredObject;
import com.agentmind.document.chunk.DocumentChunk;
import com.agentmind.document.chunk.TextChunker;
import com.agentmind.document.model.DocumentSourceType;
import com.agentmind.document.model.IngestionStatus;
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
 * Application service for document ingestion use cases.
 *
 * <p>The service keeps controllers thin and owns the current use-case flow: validate input, store raw content,
 * extract text, split text into chunks and update task/document state. Persistence is still in-memory in this
 * stage, but the parser and chunker boundaries are close to what will be used by the future database/vector flow.</p>
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
    private final AtomicLong documentIdGenerator = new AtomicLong(100);
    private final AtomicLong taskIdGenerator = new AtomicLong(1000);
    private final Map<Long, DocumentSummaryResponse> documents = new ConcurrentHashMap<>();
    private final Map<Long, IngestionTaskResponse> tasks = new ConcurrentHashMap<>();
    private final Map<Long, List<DocumentChunk>> chunksByDocumentId = new ConcurrentHashMap<>();

    public DocumentApplicationService(
            FileUploadValidator fileUploadValidator,
            ObjectStorageService objectStorageService,
            UrlSafetyValidator urlSafetyValidator,
            HtmlFetchService htmlFetchService,
            DocumentTextExtractionService textExtractionService,
            TextChunker textChunker,
            KnowledgeIndexingService knowledgeIndexingService
    ) {
        this.fileUploadValidator = fileUploadValidator;
        this.objectStorageService = objectStorageService;
        this.urlSafetyValidator = urlSafetyValidator;
        this.htmlFetchService = htmlFetchService;
        this.textExtractionService = textExtractionService;
        this.textChunker = textChunker;
        this.knowledgeIndexingService = knowledgeIndexingService;
        seedMockDocuments();
    }

    /**
     * Creates a file ingestion task and performs the current synchronous ingestion skeleton.
     *
     * <p>The method reads uploaded bytes once, stores the original object, extracts text for supported formats
     * (Markdown, TXT, HTML and code), and writes generated chunks into the in-memory chunk store. Later stages can
     * move extraction/chunking to an async worker without changing the HTTP contract.</p>
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
        OffsetDateTime createdAt = OffsetDateTime.now();
        String displayTitle = resolveDocumentTitle(title, validation.safeFilename());
        List<String> normalizedTags = normalizeTags(tags);

        putDocument(documentId, displayTitle, validation.sourceType(), workspaceId, normalizedTags,
                IngestionStatus.RUNNING, 0, createdAt);
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

            putDocument(documentId, displayTitle, validation.sourceType(), workspaceId, normalizedTags,
                    IngestionStatus.SUCCEEDED, generatedChunks.size(), OffsetDateTime.now());
            putTask(taskId, documentId, IngestionTaskType.FILE_UPLOAD, IngestionTaskStatus.SUCCEEDED,
                    100, storedObject.storageKey(), null, createdAt);
            return new FileDocumentUploadResponse(documentId, taskId, IngestionTaskStatus.SUCCEEDED);
        } catch (IOException exception) {
            markTaskFailed(documentId, taskId, displayTitle, validation.sourceType(), workspaceId,
                    normalizedTags, IngestionTaskType.FILE_UPLOAD, validation.safeFilename(),
                    "File ingestion failed");
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "File ingestion failed");
        }
    }

    /**
     * Creates a web-page ingestion task.
     *
     * <p>URL safety validation and HTML fetching are still separated from parsing. That keeps SSRF protection,
     * network fetching, text extraction and chunking independently testable.</p>
     */
    public WebPageCaptureResponse createWebPageCaptureTask(Long workspaceId, WebPageCaptureRequest request) {
        validateWorkspaceId(workspaceId);
        URI uri = urlSafetyValidator.validatePublicHttpUrl(request.url());

        Long documentId = documentIdGenerator.incrementAndGet();
        Long taskId = taskIdGenerator.incrementAndGet();
        OffsetDateTime createdAt = OffsetDateTime.now();
        String displayTitle = StringUtils.hasText(request.title()) ? request.title().trim() : uri.getHost();
        List<String> normalizedTags = normalizeTags(request.tags());

        putDocument(documentId, displayTitle, DocumentSourceType.WEB_PAGE, workspaceId, normalizedTags,
                IngestionStatus.RUNNING, 0, createdAt);
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

            putDocument(documentId, displayTitle, DocumentSourceType.WEB_PAGE, workspaceId, normalizedTags,
                    IngestionStatus.SUCCEEDED, generatedChunks.size(), OffsetDateTime.now());
            putTask(taskId, documentId, IngestionTaskType.WEB_PAGE_CAPTURE, IngestionTaskStatus.SUCCEEDED,
                    100, storedObject.storageKey(), null, createdAt);
            return new WebPageCaptureResponse(documentId, taskId, IngestionTaskStatus.SUCCEEDED);
        } catch (BusinessException exception) {
            markTaskFailed(documentId, taskId, displayTitle, DocumentSourceType.WEB_PAGE, workspaceId,
                    normalizedTags, IngestionTaskType.WEB_PAGE_CAPTURE, uri.toString(), exception.getMessage());
            return new WebPageCaptureResponse(documentId, taskId, IngestionTaskStatus.FAILED);
        } catch (IOException exception) {
            markTaskFailed(documentId, taskId, displayTitle, DocumentSourceType.WEB_PAGE, workspaceId,
                    normalizedTags, IngestionTaskType.WEB_PAGE_CAPTURE, uri.toString(), "Web page ingestion failed");
            return new WebPageCaptureResponse(documentId, taskId, IngestionTaskStatus.FAILED);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            markTaskFailed(documentId, taskId, displayTitle, DocumentSourceType.WEB_PAGE, workspaceId,
                    normalizedTags, IngestionTaskType.WEB_PAGE_CAPTURE, uri.toString(), "Web page ingestion interrupted");
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

    public List<DocumentChunkResponse> listDocumentChunks(Long workspaceId, Long documentId) {
        validateWorkspaceId(workspaceId);
        DocumentSummaryResponse document = documents.get(documentId);
        if (document == null || !Objects.equals(document.workspaceId(), workspaceId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Document not found");
        }
        return chunksByDocumentId.getOrDefault(documentId, List.of()).stream()
                .map(this::toChunkResponse)
                .toList();
    }

    public IngestionTaskResponse getTask(Long workspaceId, Long taskId) {
        validateWorkspaceId(workspaceId);
        if (taskId == null || taskId <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "taskId must be positive");
        }
        IngestionTaskResponse task = tasks.get(taskId);
        if (task == null || !Objects.equals(resolveTaskWorkspaceId(task), workspaceId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Ingestion task not found");
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
        documents.put(1L, new DocumentSummaryResponse(
                1L,
                "Java concurrency notes",
                DocumentSourceType.MARKDOWN,
                1L,
                DEFAULT_WORKSPACE_NAME,
                List.of("Java", "Concurrency", "Thread Pool"),
                IngestionStatus.SUCCEEDED,
                2,
                now.minusDays(1)
        ));
        chunksByDocumentId.put(1L, List.of(
                new DocumentChunk("1-0", 1L, 0, "Thread Pool Basics",
                        "Thread pools reuse worker threads and reduce task creation overhead.", 0, 72),
                new DocumentChunk("1-1", 1L, 1, "Executor Tuning",
                        "Core size, max size and queue policy should match workload characteristics.", 73, 146)
        ));
        knowledgeIndexingService.indexChunks(1L, 1L, chunksByDocumentId.get(1L));
        documents.put(2L, new DocumentSummaryResponse(
                2L,
                "Spring AI reference excerpt",
                DocumentSourceType.WEB_PAGE,
                1L,
                "Agent Engineering",
                List.of("Spring AI", "RAG", "Tool Calling"),
                IngestionStatus.RUNNING,
                0,
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
            throw new BusinessException(ErrorCode.BAD_REQUEST, "workspaceId must be positive");
        }
    }

    private String resolveDocumentTitle(String title, String fallbackFilename) {
        if (StringUtils.hasText(title)) {
            return title.trim();
        }
        return StringUtils.hasText(fallbackFilename) ? fallbackFilename : "Untitled document";
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
        chunksByDocumentId.remove(documentId);
        knowledgeIndexingService.deleteDocumentIndex(workspaceId, documentId);
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
        DocumentSummaryResponse document = documents.get(task.documentId());
        return document == null ? null : document.workspaceId();
    }
}
