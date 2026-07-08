package com.agentmind.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentmind.agent.audit.model.AgentToolCallStatus;
import com.agentmind.agent.audit.model.AgentToolType;
import com.agentmind.agent.audit.model.dto.AgentToolCallSummaryResponse;
import com.agentmind.chat.model.dto.RagChatResponse;
import com.agentmind.chat.model.dto.RagCitationResponse;
import com.agentmind.chat.model.dto.TokenUsageResponse;
import com.agentmind.document.model.dto.DocumentQueryRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Stage 2 契约层测试。
 *
 * <p>该测试不启动 Spring 容器、不连接数据库，只验证当前阶段新增的 DTO 和基础契约
 * 可以稳定创建和读取。它适合作为后续接口实现前的轻量回归测试。</p>
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
    void ragChatResponseShouldCarryCitationsToolsAndUsage() {
        RagCitationResponse citation = new RagCitationResponse(
                1L,
                "Java 并发编程笔记",
                100L,
                "线程池通过核心线程数、队列和拒绝策略控制任务执行。",
                0.91
        );
        AgentToolCallSummaryResponse toolCall = new AgentToolCallSummaryResponse(
                "searchDocuments",
                AgentToolType.READ,
                AgentToolCallStatus.SUCCEEDED,
                "检索到 5 个相关片段",
                128
        );
        TokenUsageResponse usage = new TokenUsageResponse(1000, 300, 1300);

        RagChatResponse response = new RagChatResponse(
                10L,
                20L,
                "线程池核心参数可以从执行能力、排队能力和过载保护三个角度理解。",
                List.of(citation),
                List.of(toolCall),
                usage
        );

        assertThat(response.citations()).hasSize(1);
        assertThat(response.toolCalls()).hasSize(1);
        assertThat(response.usage().totalTokens()).isEqualTo(1300);
    }
}
