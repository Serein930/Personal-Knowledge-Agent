package com.agentmind.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentmind.common.exception.BusinessException;
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
import com.agentmind.ingestion.model.dto.IngestionTaskResponse;
import com.agentmind.ingestion.web.HtmlFetchResult;
import com.agentmind.ingestion.web.HtmlFetchService;
import com.agentmind.ingestion.web.UrlSafetyValidator;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Stage 4 文档摄取服务测试。
 *
 * <p>这些测试不启动 Spring 容器，也不访问真实网络。通过内存版对象存储和固定 HTML 抓取器，
 * 验证文件校验、URL 安全校验、存储调用和任务状态流转是否符合当前阶段契约。</p>
 */
class DocumentApplicationServiceTests {

    private final InMemoryObjectStorageService objectStorageService = new InMemoryObjectStorageService();
    private final DocumentApplicationService service = new DocumentApplicationService(
            new FileUploadValidator(20_971_520L),
            objectStorageService,
            new UrlSafetyValidator(),
            new StaticHtmlFetchService()
    );

    @Test
    void createFileUploadTaskShouldStoreFileAndMarkTaskSucceeded() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "thread-pool.md",
                "text/markdown",
                "# Java 线程池".getBytes(StandardCharsets.UTF_8)
        );

        FileDocumentUploadResponse response = service.createFileUploadTask(
                1L,
                file,
                "线程池学习笔记",
                java.util.List.of("Java", "并发")
        );

        IngestionTaskResponse task = service.getTask(1L, response.taskId());

        assertThat(response.documentId()).isPositive();
        assertThat(response.taskId()).isPositive();
        assertThat(response.status()).isEqualTo(IngestionTaskStatus.SUCCEEDED);
        assertThat(task.status()).isEqualTo(IngestionTaskStatus.SUCCEEDED);
        assertThat(task.progress()).isEqualTo(100);
        assertThat(task.source()).contains("documents");
        assertThat(objectStorageService.storeCount).isEqualTo(1);
    }

    @Test
    void createFileUploadTaskShouldRejectUnsupportedFileExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "danger.exe",
                "application/octet-stream",
                "not allowed".getBytes(StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> service.createFileUploadTask(1L, file, null, java.util.List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Unsupported file type");
    }

    @Test
    void listDocumentsShouldFilterByKeywordAndSourceType() {
        PageResponse<DocumentSummaryResponse> response = service.listDocuments(
                1L,
                1,
                20,
                "Java",
                DocumentSourceType.MARKDOWN,
                null,
                null
        );

        assertThat(response.records()).hasSize(1);
        assertThat(response.records().getFirst().title()).contains("Java");
        assertThat(response.total()).isEqualTo(1);
    }

    @Test
    void createWebPageCaptureTaskShouldRejectLocalhostUrl() {
        WebPageCaptureRequest request = new WebPageCaptureRequest(
                "http://localhost:8080/private",
                "本地地址",
                java.util.List.of("安全")
        );

        assertThatThrownBy(() -> service.createWebPageCaptureTask(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("URL host is not allowed");
    }

    @Test
    void createWebPageCaptureTaskShouldFetchHtmlSnapshotAndMarkTaskSucceeded() {
        WebPageCaptureRequest request = new WebPageCaptureRequest(
                "https://example.com/article",
                "示例文章",
                java.util.List.of("Web")
        );

        WebPageCaptureResponse response = service.createWebPageCaptureTask(1L, request);
        IngestionTaskResponse task = service.getTask(1L, response.taskId());
        PageResponse<DocumentSummaryResponse> documents = service.listDocuments(
                1L,
                1,
                20,
                "示例文章",
                DocumentSourceType.WEB_PAGE,
                IngestionStatus.SUCCEEDED,
                null
        );

        assertThat(response.documentId()).isPositive();
        assertThat(response.taskId()).isPositive();
        assertThat(response.status()).isEqualTo(IngestionTaskStatus.SUCCEEDED);
        assertThat(task.status()).isEqualTo(IngestionTaskStatus.SUCCEEDED);
        assertThat(task.progress()).isEqualTo(100);
        assertThat(task.source()).contains("web-snapshots");
        assertThat(documents.records()).hasSize(1);
    }

    private static class InMemoryObjectStorageService implements ObjectStorageService {

        private int storeCount;

        @Override
        public StoredObject store(String category, String originalName, InputStream inputStream, long size, String contentType)
                throws IOException {
            this.storeCount++;
            byte[] bytes = inputStream.readAllBytes();
            String storageKey = category + "/" + originalName;
            return new StoredObject(storageKey, Path.of("mock-storage").resolve(originalName), originalName, contentType,
                    bytes.length);
        }
    }

    private static class StaticHtmlFetchService implements HtmlFetchService {

        @Override
        public HtmlFetchResult fetch(URI uri) {
            return new HtmlFetchResult(uri, 200, "text/html; charset=utf-8", "<html><body>AgentMind</body></html>");
        }
    }
}
