package com.agentmind.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentmind.common.exception.BusinessException;
import com.agentmind.common.response.PageResponse;
import com.agentmind.common.storage.ObjectStorageService;
import com.agentmind.common.storage.StoredObject;
import com.agentmind.document.chunk.MarkdownAwareTextChunker;
import com.agentmind.document.model.DocumentSourceType;
import com.agentmind.document.model.IngestionStatus;
import com.agentmind.document.model.dto.DocumentChunkResponse;
import com.agentmind.document.model.dto.DocumentSummaryResponse;
import com.agentmind.document.model.dto.FileDocumentUploadResponse;
import com.agentmind.document.model.dto.WebPageCaptureRequest;
import com.agentmind.document.model.dto.WebPageCaptureResponse;
import com.agentmind.document.parser.DocumentTextExtractionService;
import com.agentmind.document.parser.HtmlTextExtractor;
import com.agentmind.document.parser.MarkdownTextExtractor;
import com.agentmind.document.parser.PlainTextExtractor;
import com.agentmind.ingestion.model.IngestionTaskStatus;
import com.agentmind.ingestion.model.dto.IngestionTaskResponse;
import com.agentmind.ingestion.web.HtmlFetchResult;
import com.agentmind.ingestion.web.HtmlFetchService;
import com.agentmind.ingestion.web.UrlSafetyValidator;
import com.agentmind.knowledge.service.KnowledgeIndexingService;
import com.agentmind.knowledge.vector.DeterministicEmbeddingClient;
import com.agentmind.knowledge.vector.InMemoryVectorStore;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/**
 * 测试当前基于内存的文档摄取流程。
 *
 * <p>该测试使用假的对象存储和假的网页抓取器，在不启动应用框架、不访问网络的情况下，
 * 验证校验、提取、切分和任务状态流转。</p>
 */
class DocumentApplicationServiceTests {

    private final InMemoryObjectStorageService objectStorageService = new InMemoryObjectStorageService();
    private final DeterministicEmbeddingClient embeddingClient = new DeterministicEmbeddingClient();
    private final InMemoryVectorStore vectorStore = new InMemoryVectorStore();
    private final DocumentApplicationService service = new DocumentApplicationService(
            new FileUploadValidator(20_971_520L),
            objectStorageService,
            new UrlSafetyValidator(),
            new StaticHtmlFetchService(),
            new DocumentTextExtractionService(List.of(
                    new MarkdownTextExtractor(),
                    new PlainTextExtractor(),
                    new HtmlTextExtractor()
            )),
            new MarkdownAwareTextChunker(),
            new KnowledgeIndexingService(embeddingClient, vectorStore)
    );

    @Test
    void createFileUploadTaskShouldExtractMarkdownAndCreateChunks() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "thread-pool.md",
                "text/markdown",
                """
                # Java Thread Pool

                Thread pools reuse worker threads and reduce task creation overhead.

                ## Tuning

                Core size, max size and queue policy should match workload characteristics.
                """.getBytes(StandardCharsets.UTF_8)
        );

        FileDocumentUploadResponse response = service.createFileUploadTask(
                1L,
                file,
                "Thread pool learning note",
                List.of("Java", "Concurrency")
        );
        IngestionTaskResponse task = service.getTask(1L, response.taskId());
        List<DocumentChunkResponse> chunks = service.listDocumentChunks(1L, response.documentId());

        assertThat(response.status()).isEqualTo(IngestionTaskStatus.SUCCEEDED);
        assertThat(task.status()).isEqualTo(IngestionTaskStatus.SUCCEEDED);
        assertThat(task.progress()).isEqualTo(100);
        assertThat(task.source()).contains("documents");
        assertThat(chunks).isNotEmpty();
        assertThat(chunks.getFirst().content()).contains("Java Thread Pool");
        assertThat(vectorStore.search(1L, embeddingClient.embed("thread pool overhead"), 3))
                .isNotEmpty();
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

        assertThatThrownBy(() -> service.createFileUploadTask(1L, file, null, List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的文件类型");
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
                "Local address",
                List.of("Security")
        );

        assertThatThrownBy(() -> service.createWebPageCaptureTask(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("链接主机不允许访问");
    }

    @Test
    void createWebPageCaptureTaskShouldExtractHtmlTextAndCreateChunks() {
        WebPageCaptureRequest request = new WebPageCaptureRequest(
                "https://example.com/article",
                "Example article",
                List.of("Web")
        );

        WebPageCaptureResponse response = service.createWebPageCaptureTask(1L, request);
        IngestionTaskResponse task = service.getTask(1L, response.taskId());
        List<DocumentChunkResponse> chunks = service.listDocumentChunks(1L, response.documentId());
        PageResponse<DocumentSummaryResponse> documents = service.listDocuments(
                1L,
                1,
                20,
                "Example article",
                DocumentSourceType.WEB_PAGE,
                IngestionStatus.SUCCEEDED,
                null
        );

        assertThat(response.status()).isEqualTo(IngestionTaskStatus.SUCCEEDED);
        assertThat(task.status()).isEqualTo(IngestionTaskStatus.SUCCEEDED);
        assertThat(task.source()).contains("web-snapshots");
        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().content()).contains("AgentMind article body");
        assertThat(chunks.getFirst().content()).doesNotContain("<script>");
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
            String html = """
                    <html>
                      <head><title>AgentMind Test Page</title><script>alert('skip');</script></head>
                      <body><h1>AgentMind article body</h1><p>HTML text should become clean chunks.</p></body>
                    </html>
                    """;
            return new HtmlFetchResult(uri, 200, "text/html; charset=utf-8", html);
        }
    }
}
