package com.agentmind.dashboard.controller;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** 工作台接口统一响应和参数校验测试。 */
@SpringBootTest
@AutoConfigureMockMvc
class DashboardControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnRealWorkspaceOverview() throws Exception {
        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/dashboard", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.knowledgeAssetCount", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.data.ingestedToday", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.data.pendingIngestionCount", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.data.dueFlashcardCount", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.data.agentCallCount", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.data.recentDocuments").isArray())
                .andExpect(jsonPath("$.data.studyTasks").isArray())
                .andExpect(jsonPath("$.data.generatedAt").isString());
    }

    @Test
    void shouldRejectNonPositiveWorkspaceId() throws Exception {
        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/dashboard", 0L))
                .andExpect(status().isBadRequest());
    }
}
