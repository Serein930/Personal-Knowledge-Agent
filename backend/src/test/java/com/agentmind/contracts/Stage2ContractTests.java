package com.agentmind.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentmind.agent.audit.model.AgentToolCallStatus;
import com.agentmind.agent.audit.model.AgentToolType;
import com.agentmind.agent.audit.model.dto.AgentToolCallSummaryResponse;
import com.agentmind.chat.model.dto.RagAnswerGenerationMetadataResponse;
import com.agentmind.chat.model.dto.RagChatResponse;
import com.agentmind.chat.model.dto.RagCitationResponse;
import com.agentmind.chat.model.dto.RagRetrievalContextResponse;
import com.agentmind.chat.model.dto.TokenUsageResponse;
import com.agentmind.document.model.dto.DocumentQueryRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 数据传输对象契约级回归测试。
 *
 * <p>这些测试不会启动应用框架，也不会连接外部系统。它们用于保护数据传输对象构造方式和字段访问方式，
 * 避免项目从早期契约演进到真实接口实现时破坏已有响应结构。</p>
 */
class Stage2ContractTests {

    @Test
    void documentQueryDefaultsShouldUseFirstPageAndDefaultPageSize() {
        DocumentQueryRequest request = DocumentQueryRequest.defaults();

        assertThat(request.page()).isEqualTo(1);
        assertThat(request.pageSize()).isEqualTo(20);
        assertThat(request.keyword()).isNull();
    }

    @Test
    void ragChatResponseShouldCarryRetrievalContextCitationsToolsAndUsage() {
        RagCitationResponse citation = new RagCitationResponse(
                1,
                1L,
                "Java concurrency notes",
                "100-0",
                0,
                "Thread Pool",
                "Thread pools reuse worker threads and control backend task execution.",
                0.91
        );
        RagRetrievalContextResponse retrievalContext = new RagRetrievalContextResponse(
                "How do thread pools help backend tasks?",
                5,
                "rag-chat-v1",
                "[1] Thread pools reuse worker threads.",
                List.of(citation)
        );
        AgentToolCallSummaryResponse toolCall = new AgentToolCallSummaryResponse(
                "searchDocuments",
                AgentToolType.READ,
                AgentToolCallStatus.SUCCEEDED,
                "Retrieved 5 related chunks",
                128
        );
        TokenUsageResponse usage = new TokenUsageResponse(1000, 300, 1300);
        RagAnswerGenerationMetadataResponse generationMetadata = new RagAnswerGenerationMetadataResponse(
                "rag-chat-v1",
                "mock",
                "mock-local",
                false,
                "",
                12
        );

        RagChatResponse response = new RagChatResponse(
                10L,
                20L,
                "Thread pools can be understood through execution capacity, queueing and overload protection.",
                retrievalContext,
                List.of(citation),
                List.of(toolCall),
                generationMetadata,
                usage
        );

        assertThat(response.retrievalContext().promptContext()).contains("Thread pools");
        assertThat(response.retrievalContext().promptVersion()).isEqualTo("rag-chat-v1");
        assertThat(response.citations()).hasSize(1);
        assertThat(response.toolCalls()).hasSize(1);
        assertThat(response.generationMetadata().modelName()).isEqualTo("mock-local");
        assertThat(response.usage().totalTokens()).isEqualTo(1300);
    }
}
