package com.agentmind.agent.confirmation.controller;

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
 * 写工具确认接口安全边界测试。
 *
 * <p>覆盖禁止直接写入、一次性令牌、权限复核、拒绝状态和写入幂等语义。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class AgentToolConfirmationControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createNoteShouldRequireConfirmationAndOnlyWriteOnce() throws Exception {
        long workspaceId = 9_101L;
        String requestId = "create-note-" + UUID.randomUUID();

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/agent/tool-calls", workspaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(noteToolRequest(requestId, "确认流程", "只有确认后才能写入")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("RESOURCE_CONFLICT")));

        ConfirmationFixture fixture = createConfirmation(
                workspaceId, requestId, "确认流程", "只有确认后才能写入"
        );

        mockMvc.perform(get(
                            "/api/v1/workspaces/{workspaceId}/agent/write-tool-confirmations/{confirmationId}",
                            workspaceId,
                            fixture.id()
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", equalTo("PENDING_CONFIRMATION")))
                .andExpect(jsonPath("$.data.toolName", equalTo("note.create")))
                .andExpect(jsonPath("$.data.confirmationToken").doesNotExist())
                .andExpect(jsonPath("$.data.tokenDigest").doesNotExist())
                .andExpect(jsonPath("$.data.arguments").doesNotExist());

        confirm(workspaceId, fixture)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reused", equalTo(false)))
                .andExpect(jsonPath("$.data.confirmation.status", equalTo("SUCCEEDED")))
                .andExpect(jsonPath("$.data.confirmation.execution.audit.toolType", equalTo("WRITE")))
                .andExpect(jsonPath("$.data.confirmation.execution.result.title", equalTo("确认流程")));

        confirm(workspaceId, fixture)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reused", equalTo(true)))
                .andExpect(jsonPath("$.data.confirmation.execution.result.id", notNullValue()));

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/notes", workspaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total", equalTo(1)))
                .andExpect(jsonPath("$.data.records[0].title", equalTo("确认流程")))
                .andExpect(jsonPath("$.data.records[0].content", equalTo("只有确认后才能写入")));
    }

    @Test
    void repeatedRequestIdShouldReuseWriteButRejectDifferentArguments() throws Exception {
        long workspaceId = 9_102L;
        String requestId = "idempotent-note-" + UUID.randomUUID();
        ConfirmationFixture first = createConfirmation(workspaceId, requestId, "幂等笔记", "相同内容");
        confirm(workspaceId, first)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reused", equalTo(false)));

        ConfirmationFixture repeated = createConfirmation(workspaceId, requestId, "幂等笔记", "相同内容");
        confirm(workspaceId, repeated)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reused", equalTo(true)));

        ConfirmationFixture changed = createConfirmation(workspaceId, requestId, "幂等笔记", "不同内容");
        confirm(workspaceId, changed)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("RESOURCE_CONFLICT")));

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/notes", workspaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total", equalTo(1)));
        mockMvc.perform(get(
                            "/api/v1/workspaces/{workspaceId}/agent/write-tool-confirmations/{confirmationId}",
                            workspaceId,
                            changed.id()
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", equalTo("FAILED")));
    }

    @Test
    void confirmationShouldRecheckTokenUserAndRejectedState() throws Exception {
        long workspaceId = 9_103L;
        ConfirmationFixture fixture = createConfirmation(
                workspaceId,
                "security-note-" + UUID.randomUUID(),
                "安全边界",
                "错误令牌不能执行"
        );

        mockMvc.perform(post(
                            "/api/v1/workspaces/{workspaceId}/agent/write-tool-confirmations/{confirmationId}/confirm",
                            workspaceId,
                            fixture.id()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmationToken\":\"wrong-token\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", equalTo("FORBIDDEN")));

        mockMvc.perform(get(
                            "/api/v1/workspaces/{workspaceId}/agent/write-tool-confirmations/{confirmationId}",
                            workspaceId,
                            fixture.id()
                        )
                        .header("X-Demo-User-Id", "2"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post(
                            "/api/v1/workspaces/{workspaceId}/agent/write-tool-confirmations/{confirmationId}/reject",
                            workspaceId,
                            fixture.id()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody(fixture.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.confirmation.status", equalTo("REJECTED")));

        confirm(workspaceId, fixture)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("RESOURCE_CONFLICT")));
        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/notes", workspaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total", equalTo(0)));
    }

    @Test
    void confirmationShouldRejectReadToolInvalidArgumentsAndForeignConversation() throws Exception {
        long workspaceId = 9_104L;
        mockMvc.perform(post(
                            "/api/v1/workspaces/{workspaceId}/agent/write-tool-confirmations",
                            workspaceId
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "toolName":"knowledge.search",
                                  "requestId":"read-tool",
                                  "arguments":{"query":"Java"}
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(
                            "/api/v1/workspaces/{workspaceId}/agent/write-tool-confirmations",
                            workspaceId
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "toolName":"note.create",
                                  "requestId":"invalid-note",
                                  "arguments":{"title":"缺少正文"}
                                }
                                """))
                .andExpect(status().isBadRequest());

        MvcResult chat = mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/rag/chat", 9_105L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"创建权限测试会话\",\"topK\":3}"))
                .andExpect(status().isOk())
                .andReturn();
        long conversationId = objectMapper.readTree(chat.getResponse().getContentAsString())
                .path("data").path("conversationId").asLong();

        mockMvc.perform(post(
                            "/api/v1/workspaces/{workspaceId}/agent/write-tool-confirmations",
                            workspaceId
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "conversationId":%d,
                                  "toolName":"note.create",
                                  "requestId":"foreign-conversation",
                                  "arguments":{"title":"越权", "content":"不能创建"}
                                }
                                """.formatted(conversationId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("RESOURCE_NOT_FOUND")));
    }

    private ConfirmationFixture createConfirmation(
            long workspaceId,
            String requestId,
            String title,
            String content
    ) throws Exception {
        MvcResult result = mockMvc.perform(post(
                            "/api/v1/workspaces/{workspaceId}/agent/write-tool-confirmations",
                            workspaceId
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(noteToolRequest(requestId, title, content)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.confirmation.status", equalTo("PENDING_CONFIRMATION")))
                .andExpect(jsonPath("$.data.confirmationToken", notNullValue()))
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        return new ConfirmationFixture(
                data.path("confirmation").path("id").asLong(),
                data.path("confirmationToken").asText()
        );
    }

    private org.springframework.test.web.servlet.ResultActions confirm(
            long workspaceId,
            ConfirmationFixture fixture
    ) throws Exception {
        return mockMvc.perform(post(
                            "/api/v1/workspaces/{workspaceId}/agent/write-tool-confirmations/{confirmationId}/confirm",
                            workspaceId,
                            fixture.id()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody(fixture.token())));
    }

    private String noteToolRequest(String requestId, String title, String content) throws Exception {
        return objectMapper.writeValueAsString(objectMapper.createObjectNode()
                .put("toolName", "note.create")
                .put("requestId", requestId)
                .set("arguments", objectMapper.createObjectNode()
                        .put("title", title)
                        .put("content", content)));
    }

    private String tokenBody(String token) throws Exception {
        return objectMapper.writeValueAsString(objectMapper.createObjectNode()
                .put("confirmationToken", token));
    }

    private record ConfirmationFixture(long id, String token) {
    }
}
