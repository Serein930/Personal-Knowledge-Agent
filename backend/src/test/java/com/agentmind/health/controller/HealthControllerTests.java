package com.agentmind.health.controller;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HealthController.class)
@TestPropertySource(properties = "spring.application.name=agentmind-backend")
class HealthControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthShouldReturnUnifiedResponse() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", equalTo("SUCCESS")))
                .andExpect(jsonPath("$.data.application", equalTo("agentmind-backend")))
                .andExpect(jsonPath("$.data.status", equalTo("UP")));
    }
}
