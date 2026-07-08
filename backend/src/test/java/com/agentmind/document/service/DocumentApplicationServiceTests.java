package com.agentmind.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentmind.common.exception.BusinessException;
import com.agentmind.common.response.PageResponse;
import com.agentmind.document.model.DocumentSourceType;
import com.agentmind.document.model.dto.DocumentSummaryResponse;
import com.agentmind.document.model.dto.FileDocumentUploadResponse;
import com.agentmind.document.model.dto.WebPageCaptureRequest;
import com.agentmind.document.model.dto.WebPageCaptureResponse;
import com.agentmind.ingestion.model.IngestionTaskStatus;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Stage 3 文档摄取内存服务测试。
 *
 * <p>这些测试不依赖 Spring 容器和数据库，专门验证当前阶段的接口骨架背后
 * 是否能稳定创建 mock 任务、返回列表数据和执行基础 URL 安全校验。</p>
 */
class DocumentApplicationServiceTests {

    private final DocumentApplicationService service = new DocumentApplicationService();

    @Test
    void createFileUploadTaskShouldCreatePendingDocumentAndTask() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "thread-pool.md",
                "text/markdown",
                "# Java 线程池".getBytes()
        );

        FileDocumentUploadResponse response = service.createFileUploadTask(
                1L,
                file,
                "线程池学习笔记",
                java.util.List.of("Java", "并发")
        );

        assertThat(response.documentId()).isPositive();
        assertThat(response.taskId()).isPositive();
        assertThat(response.status()).isEqualTo(IngestionTaskStatus.PENDING);
        assertThat(service.getTask(1L, response.taskId()).documentId()).isEqualTo(response.documentId());
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
                .hasMessageContaining("URL 主机地址不允许访问");
    }

    @Test
    void createWebPageCaptureTaskShouldCreatePendingTaskForPublicUrl() {
        WebPageCaptureRequest request = new WebPageCaptureRequest(
                "https://example.com/article",
                "示例文章",
                java.util.List.of("Web")
        );

        WebPageCaptureResponse response = service.createWebPageCaptureTask(1L, request);

        assertThat(response.documentId()).isPositive();
        assertThat(response.taskId()).isPositive();
        assertThat(response.status()).isEqualTo(IngestionTaskStatus.PENDING);
    }
}
