package com.agentmind.document.service;

import com.agentmind.common.exception.BusinessException;
import com.agentmind.common.exception.ErrorCode;
import com.agentmind.common.response.PageResponse;
import com.agentmind.document.model.DocumentSourceType;
import com.agentmind.document.model.IngestionStatus;
import com.agentmind.document.model.dto.DocumentSummaryResponse;
import com.agentmind.document.model.dto.FileDocumentUploadResponse;
import com.agentmind.document.model.dto.WebPageCaptureRequest;
import com.agentmind.document.model.dto.WebPageCaptureResponse;
import com.agentmind.ingestion.model.IngestionTaskStatus;
import com.agentmind.ingestion.model.IngestionTaskType;
import com.agentmind.ingestion.model.dto.IngestionTaskResponse;
import java.net.URI;
import java.net.URISyntaxException;
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
 * <p>当前阶段只使用内存 Map 模拟文档和摄取任务，目的是让前端知识库、采集中心
 * 可以尽早联调接口契约。该类不连接数据库、不保存真实文件、不执行网页抓取。</p>
 */
@Service
public class DocumentApplicationService {

    private static final long MOCK_USER_ID = 1L;
    private static final String DEFAULT_WORKSPACE_NAME = "Java 后端学习";
    private static final int MAX_PAGE_SIZE = 100;

    private final AtomicLong documentIdGenerator = new AtomicLong(100);
    private final AtomicLong taskIdGenerator = new AtomicLong(1000);
    private final Map<Long, DocumentSummaryResponse> documents = new ConcurrentHashMap<>();
    private final Map<Long, IngestionTaskResponse> tasks = new ConcurrentHashMap<>();

    public DocumentApplicationService() {
        seedMockDocuments();
    }

    /**
     * 创建文件上传摄取任务。
     *
     * <p>Stage 3 只验证接口和任务流转契约，因此文件不会写入磁盘或对象存储。
     * 后续接入 MinIO 时，可在该方法中替换为真实文件存储和异步任务创建。</p>
     */
    public FileDocumentUploadResponse createFileUploadTask(
            Long workspaceId,
            MultipartFile file,
            String title,
            List<String> tags
    ) {
        validateWorkspaceId(workspaceId);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "上传文件不能为空");
        }

        Long documentId = documentIdGenerator.incrementAndGet();
        Long taskId = taskIdGenerator.incrementAndGet();
        String displayTitle = StringUtils.hasText(title) ? title : file.getOriginalFilename();
        String safeTitle = StringUtils.hasText(displayTitle) ? displayTitle : "未命名文件";
        OffsetDateTime now = OffsetDateTime.now();

        documents.put(documentId, new DocumentSummaryResponse(
                documentId,
                safeTitle,
                resolveSourceType(file.getOriginalFilename()),
                workspaceId,
                DEFAULT_WORKSPACE_NAME,
                normalizeTags(tags),
                IngestionStatus.PENDING,
                0,
                now
        ));
        tasks.put(taskId, new IngestionTaskResponse(
                taskId,
                documentId,
                IngestionTaskType.FILE_UPLOAD,
                IngestionTaskStatus.PENDING,
                0,
                file.getOriginalFilename(),
                null,
                now,
                now
        ));

        return new FileDocumentUploadResponse(documentId, taskId, IngestionTaskStatus.PENDING);
    }

    /**
     * 创建网页采集摄取任务。
     *
     * <p>当前只做基础 URL 安全校验和内存任务创建。真正的 HTML 抓取、正文提取、
     * 去噪、重复检测和版本管理会在后续网页采集阶段实现。</p>
     */
    public WebPageCaptureResponse createWebPageCaptureTask(Long workspaceId, WebPageCaptureRequest request) {
        validateWorkspaceId(workspaceId);
        URI uri = parseAndValidatePublicHttpUrl(request.url());

        Long documentId = documentIdGenerator.incrementAndGet();
        Long taskId = taskIdGenerator.incrementAndGet();
        OffsetDateTime now = OffsetDateTime.now();
        String title = StringUtils.hasText(request.title()) ? request.title() : uri.getHost();

        documents.put(documentId, new DocumentSummaryResponse(
                documentId,
                title,
                DocumentSourceType.WEB_PAGE,
                workspaceId,
                DEFAULT_WORKSPACE_NAME,
                normalizeTags(request.tags()),
                IngestionStatus.PENDING,
                0,
                now
        ));
        tasks.put(taskId, new IngestionTaskResponse(
                taskId,
                documentId,
                IngestionTaskType.WEB_PAGE_CAPTURE,
                IngestionTaskStatus.PENDING,
                0,
                uri.toString(),
                null,
                now,
                now
        ));

        return new WebPageCaptureResponse(documentId, taskId, IngestionTaskStatus.PENDING);
    }

    /**
     * 查询文档列表。
     *
     * <p>这里模拟分页、关键词、来源类型、状态和标签过滤。后续接入数据库时，
     * 该方法会替换为 Repository 查询，但 Controller 契约保持不变。</p>
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

    private DocumentSourceType resolveSourceType(String filename) {
        if (!StringUtils.hasText(filename)) {
            return DocumentSourceType.TEXT;
        }
        String lowerName = filename.toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".pdf")) {
            return DocumentSourceType.PDF;
        }
        if (lowerName.endsWith(".md") || lowerName.endsWith(".markdown")) {
            return DocumentSourceType.MARKDOWN;
        }
        if (lowerName.endsWith(".doc") || lowerName.endsWith(".docx")) {
            return DocumentSourceType.WORD;
        }
        if (lowerName.endsWith(".java") || lowerName.endsWith(".kt") || lowerName.endsWith(".ts")) {
            return DocumentSourceType.CODE;
        }
        return DocumentSourceType.TEXT;
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

    private URI parseAndValidatePublicHttpUrl(String url) {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "URL 只允许 http 或 https");
            }
            if (!StringUtils.hasText(host) || isBlockedHost(host)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "URL 主机地址不允许访问");
            }
            return uri;
        } catch (URISyntaxException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "URL 格式不合法");
        }
    }

    private boolean isBlockedHost(String host) {
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        return "localhost".equals(normalizedHost)
                || normalizedHost.startsWith("127.")
                || normalizedHost.startsWith("10.")
                || normalizedHost.startsWith("192.168.")
                || normalizedHost.startsWith("169.254.")
                || normalizedHost.equals("0.0.0.0")
                || normalizedHost.equals("::1");
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
