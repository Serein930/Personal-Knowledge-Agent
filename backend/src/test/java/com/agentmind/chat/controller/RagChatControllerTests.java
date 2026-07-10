package com.agentmind.chat.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 检索增强生成问答接口测试。
 *
 * <p>该测试启动完整应用上下文，但仍使用默认内存向量库和模拟回答生成器。这样可以验证控制层、
 * 检索编排、拒答策略和统一响应结构之间的真实协作关系。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class RagChatControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void chatShouldReturnRefusalWhenKnowledgeBaseHasNoReliableContext() throws Exception {
        mockMvc.perform(post("/api/v1/workspaces/1/rag/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "当前知识库如何解释一个不存在的主题？",
                                  "topK": 3
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", equalTo("SUCCESS")))
                .andExpect(jsonPath("$.data.answer", containsString("当前知识库没有检索到")))
                .andExpect(jsonPath("$.data.retrievalContext.promptVersion", equalTo("rag-chat-v1")))
                .andExpect(jsonPath("$.data.generationMetadata.promptVersion", equalTo("rag-chat-v1")))
                .andExpect(jsonPath("$.data.generationMetadata.answerGenerator", equalTo("mock")))
                .andExpect(jsonPath("$.data.generationMetadata.refused", equalTo(true)))
                .andExpect(jsonPath("$.data.usage.totalTokens", equalTo(0)));
    }
}
