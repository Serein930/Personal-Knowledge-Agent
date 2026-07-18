package com.agentmind.user.controller;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** 设置页偏好读取、保存、校验和并发冲突接口测试。 */
@SpringBootTest
@AutoConfigureMockMvc
class UserWorkspacePreferenceControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReadDefaultsThenPersistWorkspacePreference() throws Exception {
        long workspaceId = 71_001L;

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/preferences", workspaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.persisted", equalTo(false)))
                .andExpect(jsonPath("$.data.version", equalTo(0)))
                .andExpect(jsonPath("$.data.defaultTopK", equalTo(5)))
                .andExpect(jsonPath("$.data.citationPolicy", equalTo("REQUIRED")));

        mockMvc.perform(put("/api/v1/workspaces/{workspaceId}/preferences", workspaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "chatModel":"qwen-plus",
                                  "embeddingModel":"text-embedding-v3",
                                  "citationPolicy":"WHEN_AVAILABLE",
                                  "defaultTopK":8,
                                  "expectedVersion":0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.persisted", equalTo(true)))
                .andExpect(jsonPath("$.data.version", equalTo(1)))
                .andExpect(jsonPath("$.data.chatModel", equalTo("qwen-plus")))
                .andExpect(jsonPath("$.data.embeddingModel", equalTo("text-embedding-v3")))
                .andExpect(jsonPath("$.data.defaultTopK", equalTo(8)));

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/preferences", workspaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.persisted", equalTo(true)))
                .andExpect(jsonPath("$.data.version", equalTo(1)))
                .andExpect(jsonPath("$.data.citationPolicy", equalTo("WHEN_AVAILABLE")));
    }

    @Test
    void shouldRejectStaleVersionAndInvalidTopK() throws Exception {
        long workspaceId = 71_002L;
        String validRequest = """
                {
                  "chatModel":"gpt-4o-mini",
                  "embeddingModel":"text-embedding-3-small",
                  "citationPolicy":"REQUIRED",
                  "defaultTopK":5,
                  "expectedVersion":0
                }
                """;
        mockMvc.perform(put("/api/v1/workspaces/{workspaceId}/preferences", workspaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/workspaces/{workspaceId}/preferences", workspaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("RESOURCE_CONFLICT")));

        mockMvc.perform(put("/api/v1/workspaces/{workspaceId}/preferences", 71_003L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "chatModel":"gpt-4o-mini",
                                  "embeddingModel":"text-embedding-3-small",
                                  "citationPolicy":"REQUIRED",
                                  "defaultTopK":21,
                                  "expectedVersion":0
                                }
                                """))
                .andExpect(status().isBadRequest());
    }
}
