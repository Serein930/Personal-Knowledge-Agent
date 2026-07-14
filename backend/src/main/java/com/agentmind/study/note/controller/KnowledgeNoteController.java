package com.agentmind.study.note.controller;

import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.common.response.ApiResponse;
import com.agentmind.common.response.PageResponse;
import com.agentmind.study.note.model.dto.KnowledgeNoteResponse;
import com.agentmind.study.note.service.KnowledgeNoteApplicationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识笔记查询接口。
 *
 * <p>当前阶段只提供查询，不提供绕过写工具确认流程的普通创建接口。</p>
 */
@Validated
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/notes")
public class KnowledgeNoteController {

    private final KnowledgeNoteApplicationService noteApplicationService;

    public KnowledgeNoteController(KnowledgeNoteApplicationService noteApplicationService) {
        this.noteApplicationService = noteApplicationService;
    }

    @GetMapping
    public ApiResponse<PageResponse<KnowledgeNoteResponse>> list(
            @PathVariable @Positive(message = "知识空间编号必须为正数") Long workspaceId,
            @RequestHeader(name = "X-Demo-User-Id", defaultValue = "1")
            @Positive(message = "演示用户编号必须为正数") Long ownerUserId,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码必须大于0") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "每页数量必须大于0")
            @Max(value = 100, message = "每页数量不能大于100") int pageSize
    ) {
        return ApiResponse.success(noteApplicationService.list(
                new AgentToolExecutionContext(ownerUserId, workspaceId, null),
                page,
                pageSize
        ));
    }
}
