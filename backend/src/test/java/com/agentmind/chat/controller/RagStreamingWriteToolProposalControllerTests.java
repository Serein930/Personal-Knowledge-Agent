package com.agentmind.chat.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agentmind.document.chunk.DocumentChunk;
import com.agentmind.knowledge.service.KnowledgeIndexingService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 流式问答写工具建议事件测试。
 *
 * <p>验证模型回答只能创建待确认单，不能在没有用户确认时保存复习卡片。</p>
 */
@SpringBootTest(properties = "agentmind.rag.minimum-citation-score=-1")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class RagStreamingWriteToolProposalControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private KnowledgeIndexingService knowledgeIndexingService;

    @Test
    void streamShouldEmitPendingFlashcardConfirmationWithoutExecutingWrite() throws Exception {
        long workspaceId = 9_301L;
        knowledgeIndexingService.indexChunks(workspaceId, 93_010L, List.of(
                new DocumentChunk(
                        "93010-0",
                        93_010L,
                        0,
                        "线程池",
                        "请根据知识库回答生成一张复习卡片",
                        0,
                        18
                )
        ));
        MvcResult started = mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/rag/chat/stream", workspaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("""
                                {"question":"请根据知识库回答生成一张复习卡片","topK":3}
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult completed = mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("event:tool_confirmation_required")))
                .andExpect(content().string(containsString("\"toolName\":\"flashcard.create\"")))
                .andExpect(content().string(containsString("\"status\":\"PENDING_CONFIRMATION\"")))
                .andExpect(content().string(containsString("\"confirmationToken\":")))
                .andExpect(content().string(containsString("event:complete")))
                .andReturn();

        String stream = completed.getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(stream.indexOf("event:tool_confirmation_required"))
                .isGreaterThan(stream.indexOf("event:delta"));
        org.assertj.core.api.Assertions.assertThat(stream.indexOf("event:complete"))
                .isGreaterThan(stream.indexOf("event:tool_confirmation_required"));

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/flashcards", workspaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total", equalTo(0)));
    }

    @Test
    void streamShouldNotProposeFlashcardWhenKnowledgeAnswerIsRefused() throws Exception {
        long workspaceId = 9_302L;
        MvcResult started = mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/rag/chat/stream", workspaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("""
                                {"question":"请根据不存在的资料生成一张复习卡片","topK":3}
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult completed = mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("event:complete")))
                .andReturn();

        org.assertj.core.api.Assertions.assertThat(completed.getResponse().getContentAsString())
                .doesNotContain("event:tool_confirmation_required");
    }
}
