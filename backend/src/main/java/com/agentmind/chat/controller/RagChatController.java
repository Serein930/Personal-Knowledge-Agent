package com.agentmind.chat.controller;

import com.agentmind.chat.model.dto.RagChatRequest;
import com.agentmind.chat.model.dto.RagChatResponse;
import com.agentmind.chat.service.RagContextAssemblyService;
import com.agentmind.chat.service.RagStreamingChatService;
import com.agentmind.common.response.ApiResponse;
import com.agentmind.common.security.CurrentUserId;
import com.agentmind.workspace.service.WorkspaceAccessService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.nio.charset.StandardCharsets;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 检索增强生成问答接口。
 *
 * <p>该接口负责接收用户问题并返回回答、检索上下文和引用来源。真实模型调用不会放在控制层中，
 * 而是通过后端服务层的回答生成端口进行适配。</p>
 */
@Validated
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/rag")
public class RagChatController {

    private static final MediaType TEXT_EVENT_STREAM_UTF8 =
            new MediaType("text", "event-stream", StandardCharsets.UTF_8);

    private final RagContextAssemblyService ragContextAssemblyService;
    private final RagStreamingChatService ragStreamingChatService;
    private final WorkspaceAccessService workspaceAccessService;

    public RagChatController(
            RagContextAssemblyService ragContextAssemblyService,
            RagStreamingChatService ragStreamingChatService,
            WorkspaceAccessService workspaceAccessService
    ) {
        this.ragContextAssemblyService = ragContextAssemblyService;
        this.ragStreamingChatService = ragStreamingChatService;
        this.workspaceAccessService = workspaceAccessService;
    }

    @PostMapping("/chat")
    public ApiResponse<RagChatResponse> chat(
            @CurrentUserId Long ownerUserId,
            @PathVariable @Positive(message = "知识空间编号必须为正数") Long workspaceId,
            @Valid @RequestBody RagChatRequest request
    ) {
        workspaceAccessService.requireReadable(ownerUserId, workspaceId);
        return ApiResponse.success(ragContextAssemblyService.prepareChatContext(workspaceId, request));
    }

    /**
     * 以服务器发送事件协议返回检索增强生成回答。
     *
     * <p>响应关闭代理缓冲和客户端缓存，避免文本增量被中间层攒成完整响应后才交给前端。</p>
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> streamChat(
            @PathVariable @Positive(message = "知识空间编号必须为正数") Long workspaceId,
            @CurrentUserId
            @Positive(message = "当前用户编号必须为正数") Long ownerUserId,
            @Valid @RequestBody RagChatRequest request
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Accel-Buffering", "no")
                .contentType(TEXT_EVENT_STREAM_UTF8)
                .body(ragStreamingChatService.stream(ownerUserId, workspaceId, request));
    }
}
