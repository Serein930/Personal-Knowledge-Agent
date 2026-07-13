package com.agentmind.chat.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agentmind.document.chunk.DocumentChunk;
import com.agentmind.knowledge.service.KnowledgeIndexingService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 检索增强生成流式问答接口测试。
 *
 * <p>测试使用独立知识空间和默认模拟生成器，验证异步响应、SSE 协议事件以及唯一最终审计记录。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class RagStreamingChatControllerTests {

    private static final long WORKSPACE_ID = 777L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private KnowledgeIndexingService knowledgeIndexingService;

    @Test
    void streamChatShouldReturnOrderedSseEventsAndOneFinalObservation() throws Exception {
        knowledgeIndexingService.indexChunks(WORKSPACE_ID, 7_770L, List.of(
                new DocumentChunk(
                        "7770-0",
                        7_770L,
                        0,
                        "Thread Pool",
                        "Thread pools reuse backend worker threads and reduce task creation overhead.",
                        0,
                        78
                )
        ));

        MvcResult startedResult = mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/rag/chat/stream", WORKSPACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("""
                                {
                                  "question": "How do thread pools help backend tasks?",
                                  "topK": 3
                                }
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult completedResult = mockMvc.perform(asyncDispatch(startedResult))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("X-Accel-Buffering", equalTo("no")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(containsString("event:metadata")))
                .andExpect(content().string(containsString("event:citation")))
                .andExpect(content().string(containsString("event:delta")))
                .andExpect(content().string(containsString("event:complete")))
                .andReturn();

        String eventStream = completedResult.getResponse().getContentAsString();
        assertEventOrder(
                eventStream,
                "event:metadata",
                "event:citation",
                "event:delta",
                "event:complete"
        );

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/rag/model-calls", WORKSPACE_ID)
                        .param("page", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total", equalTo(1)))
                .andExpect(jsonPath("$.data.records[0].answerGenerator", equalTo("mock-stream")))
                .andExpect(jsonPath("$.data.records[0].citationCount", equalTo(1)))
                .andExpect(jsonPath("$.data.records[0].status", equalTo("SUCCEEDED")));
    }

    private void assertEventOrder(
            String eventStream,
            String first,
            String second,
            String third,
            String fourth
    ) {
        int firstIndex = eventStream.indexOf(first);
        int secondIndex = eventStream.indexOf(second);
        int thirdIndex = eventStream.indexOf(third);
        int fourthIndex = eventStream.indexOf(fourth);
        org.assertj.core.api.Assertions.assertThat(firstIndex).isGreaterThanOrEqualTo(0);
        org.assertj.core.api.Assertions.assertThat(secondIndex).isGreaterThan(firstIndex);
        org.assertj.core.api.Assertions.assertThat(thirdIndex).isGreaterThan(secondIndex);
        org.assertj.core.api.Assertions.assertThat(fourthIndex).isGreaterThan(thirdIndex);
    }
}
