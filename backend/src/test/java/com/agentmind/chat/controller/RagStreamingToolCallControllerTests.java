package com.agentmind.chat.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agentmind.agent.audit.model.AgentToolCallStatus;
import com.agentmind.agent.audit.model.AgentToolType;
import com.agentmind.agent.audit.model.dto.AgentToolCallSummaryResponse;
import com.agentmind.chat.model.dto.RagAnswerGenerationMetadataResponse;
import com.agentmind.chat.model.dto.TokenUsageResponse;
import com.agentmind.chat.service.AnswerGenerationRequest;
import com.agentmind.chat.service.RagStreamCancellationCheck;
import com.agentmind.chat.service.StreamingAnswerGenerator;
import com.agentmind.chat.service.StreamingGeneratedAnswer;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 流式问答工具调用事件协议测试。
 */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import(RagStreamingToolCallControllerTests.ToolCallStreamingConfiguration.class)
class RagStreamingToolCallControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void streamShouldEmitToolCallBeforeCompleteAndIncludeSummaryInCompleteEvent() throws Exception {
        MvcResult started = mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/rag/chat/stream", 880L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("""
                                {"question":"请使用知识检索工具回答","topK":3}
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        awaitStreamEvent(started, "event:complete", 5_000L);
        MvcResult completed = mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("event:delta")))
                .andExpect(content().string(containsString("event:tool_call")))
                .andExpect(content().string(containsString("\"toolName\":\"knowledge.search\"")))
                .andExpect(content().string(containsString("event:complete")))
                .andReturn();

        String stream = completed.getResponse().getContentAsString();
        assertThatEventOrder(stream, "event:delta", "event:tool_call", "event:complete");
    }

    private void awaitStreamEvent(MvcResult result, String expectedEvent, long timeoutMillis)
            throws InterruptedException, java.io.UnsupportedEncodingException {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadline) {
            if (result.getResponse().getContentAsString().contains(expectedEvent)) {
                return;
            }
            // SseEmitter 在独立线程写响应，轮询只用于消除 asyncDispatch 抢先执行的测试竞态。
            Thread.sleep(10L);
        }
        org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentAsString())
                .as("等待流式事件超时")
                .contains(expectedEvent);
    }

    private void assertThatEventOrder(String stream, String first, String second, String third) {
        org.assertj.core.api.Assertions.assertThat(stream.indexOf(first)).isGreaterThanOrEqualTo(0);
        org.assertj.core.api.Assertions.assertThat(stream.indexOf(second)).isGreaterThan(stream.indexOf(first));
        org.assertj.core.api.Assertions.assertThat(stream.indexOf(third)).isGreaterThan(stream.indexOf(second));
    }

    @TestConfiguration
    static class ToolCallStreamingConfiguration {

        @Bean
        @Primary
        StreamingAnswerGenerator toolCallStreamingAnswerGenerator() {
            return new StreamingAnswerGenerator() {
                @Override
                public String generatorType() {
                    return "tool-call-test";
                }

                @Override
                public String modelName() {
                    return "tool-call-test-model";
                }

                @Override
                public StreamingGeneratedAnswer generate(
                        AnswerGenerationRequest request,
                        Consumer<String> deltaConsumer,
                        RagStreamCancellationCheck cancellationCheck
                ) {
                    cancellationCheck.check();
                    String answer = "工具调用后的测试回答";
                    deltaConsumer.accept(answer);
                    AgentToolCallSummaryResponse summary = new AgentToolCallSummaryResponse(
                            "knowledge.search",
                            AgentToolType.READ,
                            AgentToolCallStatus.SUCCEEDED,
                            "知识检索完成，返回1个片段",
                            2
                    );
                    return new StreamingGeneratedAnswer(
                            answer.length(),
                            new TokenUsageResponse(0, 0, 0),
                            new RagAnswerGenerationMetadataResponse(
                                    request.promptVersion(),
                                    generatorType(),
                                    modelName(),
                                    false,
                                    "",
                                    2
                            ),
                            List.of(summary)
                    );
                }
            };
        }
    }
}
