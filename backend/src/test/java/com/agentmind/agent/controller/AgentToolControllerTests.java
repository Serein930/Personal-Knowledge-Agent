package com.agentmind.agent.controller;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 智能体工具调用接口测试。
 *
 * <p>覆盖白名单、工具参数、知识空间会话归属、演示用户权限和请求编号幂等语义。
 * 这些边界即使未来由 Spring AI 触发工具调用，也必须保持不变。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class AgentToolControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void readOnlyToolsShouldReturnWorkspaceScopedKnowledgeAndChunk() throws Exception {
        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/agent/tool-calls", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "toolName": "knowledge.search",
                                  "requestId": "%s",
                                  "arguments": {"query": "worker threads", "topK": 3}
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reused", equalTo(false)))
                .andExpect(jsonPath("$.data.audit.toolName", equalTo("knowledge.search")))
                .andExpect(jsonPath("$.data.audit.toolType", equalTo("READ")))
                .andExpect(jsonPath("$.data.audit.status", equalTo("SUCCEEDED")))
                .andExpect(jsonPath("$.data.result.results[0].documentId", equalTo(1)));

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/agent/tool-calls", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "toolName": "document.read_chunk",
                                  "arguments": {"documentId": 1, "chunkId": "1-0"}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.audit.status", equalTo("SUCCEEDED")))
                .andExpect(jsonPath("$.data.result.id", equalTo("1-0")))
                .andExpect(jsonPath("$.data.result.documentId", equalTo(1)));
    }

    @Test
    void repeatedRequestIdShouldReuseSuccessfulAuditWithoutExecutingAgain() throws Exception {
        String requestId = "repeat-" + UUID.randomUUID();
        String requestBody = """
                {
                  "toolName": "knowledge.search",
                  "requestId": "%s",
                  "arguments": {"query": "thread pool", "topK": 2}
                }
                """.formatted(requestId);

        MvcResult firstCall = mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/agent/tool-calls", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reused", equalTo(false)))
                .andReturn();
        long firstAuditId = objectMapper.readTree(firstCall.getResponse().getContentAsString())
                .path("data").path("audit").path("id").asLong();

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/agent/tool-calls", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reused", equalTo(true)))
                .andExpect(jsonPath("$.data.audit.id", equalTo((int) firstAuditId)))
                .andExpect(jsonPath("$.data.result", notNullValue()));
    }

    @Test
    void toolCallsShouldRejectUnknownToolInvalidArgumentsAndUnauthorizedUser() throws Exception {
        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/agent/tool-calls", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"toolName":"system.delete_all","arguments":{}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("BAD_REQUEST")));

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/agent/tool-calls", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"toolName":"knowledge.search","arguments":{"topK":3}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("BAD_REQUEST")));

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/agent/tool-calls", 1L)
                        .header("X-Demo-User-Id", "2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"toolName":"knowledge.search","arguments":{"query":"Java"}}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", equalTo("FORBIDDEN")));
    }

    @Test
    void toolCallsShouldRejectConversationFromAnotherWorkspace() throws Exception {
        MvcResult chat = mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/rag/chat", 811L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"创建用于权限验证的会话","topK":3}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode response = objectMapper.readTree(chat.getResponse().getContentAsString());
        long conversationId = response.path("data").path("conversationId").asLong();

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/agent/tool-calls", 812L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "conversationId": %d,
                                  "toolName":"knowledge.search",
                                  "arguments":{"query":"越权会话"}
                                }
                                """.formatted(conversationId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("RESOURCE_NOT_FOUND")));
    }

    @Test
    void auditListShouldOnlyReturnCurrentWorkspaceRecords() throws Exception {
        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/agent/tool-calls", 821L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"toolName":"knowledge.search","arguments":{"query":"隔离测试"}}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/agent/tool-calls", 822L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total", equalTo(0)));
    }

    @Test
    void failedToolCallShouldPersistFinalFailureAuditWithoutPendingRecord() throws Exception {
        long workspaceId = 823L;
        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/agent/tool-calls", workspaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"toolName":"system.unknown","requestId":"failed-audit-823","arguments":{}}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/agent/tool-calls", workspaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total", equalTo(1)))
                .andExpect(jsonPath("$.data.records[0].status", equalTo("FAILED")))
                .andExpect(jsonPath("$.data.records[0].toolName", equalTo("system.unknown")));
    }
}
