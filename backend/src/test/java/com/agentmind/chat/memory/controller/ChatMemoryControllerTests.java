package com.agentmind.chat.memory.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 短期会话记忆接口与知识空间权限边界测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ChatMemoryControllerTests {

    private static final long WORKSPACE_ID = 901L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void conversationApisShouldReturnHistoryAndRejectCrossWorkspaceAccess() throws Exception {
        MvcResult firstChat = mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/rag/chat", WORKSPACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "第一轮会话问题",
                                  "topK": 3
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode firstResponse = objectMapper.readTree(firstChat.getResponse().getContentAsString());
        long conversationId = firstResponse.path("data").path("conversationId").asLong();

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/rag/chat", WORKSPACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "conversationId": %d,
                                  "question": "第二轮会话问题",
                                  "topK": 3
                                }
                                """.formatted(conversationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversationId", equalTo((int) conversationId)))
                .andExpect(jsonPath("$.data.retrievalContext.promptContext", containsString("用户：第一轮会话问题")));

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/chat/conversations", WORKSPACE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total", equalTo(1)))
                .andExpect(jsonPath("$.data.records[0].id", equalTo((int) conversationId)));

        mockMvc.perform(get(
                        "/api/v1/workspaces/{workspaceId}/chat/conversations/{conversationId}/messages",
                        WORKSPACE_ID,
                        conversationId
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total", equalTo(4)))
                .andExpect(jsonPath("$.data.records[0].role", equalTo("USER")))
                .andExpect(jsonPath("$.data.records[1].role", equalTo("ASSISTANT")))
                .andExpect(jsonPath("$.data.records[1].status", equalTo("COMPLETED")));

        mockMvc.perform(get(
                        "/api/v1/workspaces/{workspaceId}/chat/conversations/{conversationId}/messages",
                        902L,
                        conversationId
                ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("RESOURCE_NOT_FOUND")))
                .andExpect(jsonPath("$.message", equalTo("会话不存在或无权访问")));

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/rag/chat", 902L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "conversationId": %d,
                                  "question": "尝试跨知识空间继续会话",
                                  "topK": 3
                                }
                                """.formatted(conversationId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("RESOURCE_NOT_FOUND")))
                .andExpect(jsonPath("$.message", equalTo("会话不存在或无权访问")));
    }

    @Test
    void managementApisShouldRenameArchiveDeleteAndEnforceLifecycle() throws Exception {
        long workspaceId = 903L;
        MvcResult firstChat = mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/rag/chat", workspaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "用于管理的会话",
                                  "topK": 3
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        long conversationId = objectMapper.readTree(firstChat.getResponse().getContentAsString())
                .path("data")
                .path("conversationId")
                .asLong();

        mockMvc.perform(patch(
                        "/api/v1/workspaces/{workspaceId}/chat/conversations/{conversationId}",
                        workspaceId,
                        conversationId
                ).contentType(MediaType.APPLICATION_JSON).content("""
                        {"title":"  Java   Agent 学习会话  "}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title", equalTo("Java Agent 学习会话")))
                .andExpect(jsonPath("$.data.status", equalTo("ACTIVE")));

        mockMvc.perform(patch(
                        "/api/v1/workspaces/{workspaceId}/chat/conversations/{conversationId}",
                        workspaceId,
                        conversationId
                ).contentType(MediaType.APPLICATION_JSON).content("""
                        {"title":"   "}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("BAD_REQUEST")));

        mockMvc.perform(patch(
                        "/api/v1/workspaces/{workspaceId}/chat/conversations/{conversationId}",
                        904L,
                        conversationId
                ).contentType(MediaType.APPLICATION_JSON).content("""
                        {"title":"越权修改"}
                        """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("RESOURCE_NOT_FOUND")));

        mockMvc.perform(post(
                        "/api/v1/workspaces/{workspaceId}/chat/conversations/{conversationId}/archive",
                        workspaceId,
                        conversationId
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", equalTo("ARCHIVED")));

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/rag/chat", workspaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "conversationId": %d,
                                  "question": "尝试继续归档会话",
                                  "topK": 3
                                }
                                """.formatted(conversationId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("RESOURCE_CONFLICT")))
                .andExpect(jsonPath("$.message", equalTo("归档会话不能继续问答")));

        mockMvc.perform(delete(
                        "/api/v1/workspaces/{workspaceId}/chat/conversations/{conversationId}",
                        workspaceId,
                        conversationId
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", equalTo("会话已删除")));

        mockMvc.perform(get(
                        "/api/v1/workspaces/{workspaceId}/chat/conversations/{conversationId}/messages",
                        workspaceId,
                        conversationId
                ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("RESOURCE_NOT_FOUND")));
    }
}
