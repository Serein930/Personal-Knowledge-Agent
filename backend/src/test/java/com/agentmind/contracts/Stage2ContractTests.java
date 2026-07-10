package com.agentmind.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentmind.agent.audit.model.AgentToolCallStatus;
import com.agentmind.agent.audit.model.AgentToolType;
import com.agentmind.agent.audit.model.dto.AgentToolCallSummaryResponse;
import com.agentmind.chat.model.dto.RagChatResponse;
import com.agentmind.chat.model.dto.RagCitationResponse;
import com.agentmind.chat.model.dto.RagRetrievalContextResponse;
import com.agentmind.chat.model.dto.TokenUsageResponse;
import com.agentmind.document.model.dto.DocumentQueryRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Contract-level DTO regression tests.
 *
 * <p>These tests do not start Spring or connect to external systems. They protect DTO construction and field access
 * while the project evolves from early contracts to concrete API implementations.</p>
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

        RagChatResponse response = new RagChatResponse(
                10L,
                20L,
                "Thread pools can be understood through execution capacity, queueing and overload protection.",
                retrievalContext,
                List.of(citation),
                List.of(toolCall),
                usage
        );

        assertThat(response.retrievalContext().promptContext()).contains("Thread pools");
        assertThat(response.citations()).hasSize(1);
        assertThat(response.toolCalls()).hasSize(1);
        assertThat(response.usage().totalTokens()).isEqualTo(1300);
    }
}
