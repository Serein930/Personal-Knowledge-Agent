package com.agentmind.chat.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agentmind.chat.service.StreamingAnswerGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 流式生成异常协议测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
class RagStreamingChatErrorControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StreamingAnswerGenerator streamingAnswerGenerator;

    @Test
    void streamChatShouldConvertGenerationFailureToErrorEvent() throws Exception {
        given(streamingAnswerGenerator.generatorType()).willReturn("failing-test");
        given(streamingAnswerGenerator.modelName()).willReturn("failing-model");
        given(streamingAnswerGenerator.generate(any(), any(), any()))
                .willThrow(new IllegalStateException("测试生成失败"));

        MvcResult startedResult = mockMvc.perform(post("/api/v1/workspaces/778/rag/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("""
                                {
                                  "question": "验证流式错误事件",
                                  "topK": 3
                                }
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(startedResult))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("event:error")))
                .andExpect(content().string(containsString("STREAM_GENERATION_FAILED")))
                .andExpect(content().string(containsString("流式回答生成失败，请稍后重试")));
    }
}
