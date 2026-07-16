package com.agentmind.knowledge.controller;

import com.agentmind.agent.service.AgentToolExecutionAuthorizer;
import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.common.response.ApiResponse;
import com.agentmind.knowledge.outbox.model.KnowledgeIndexOutboxStatistics;
import com.agentmind.knowledge.outbox.model.KnowledgeIndexRebuildResult;
import com.agentmind.knowledge.outbox.repository.KnowledgeIndexOutboxRepository;
import com.agentmind.knowledge.outbox.service.KnowledgeIndexOutboxWorker;
import com.agentmind.knowledge.outbox.service.KnowledgeIndexRebuildService;
import jakarta.validation.constraints.Positive;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 生产索引的重建、积压观察和人工恢复接口。 */
@Validated
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/knowledge/index-operations")
@ConditionalOnProperty(prefix = "agentmind.knowledge-index.outbox", name = "enabled", havingValue = "true")
public class KnowledgeIndexOperationsController {

    private final KnowledgeIndexOutboxRepository repository;
    private final KnowledgeIndexOutboxWorker worker;
    private final KnowledgeIndexRebuildService rebuildService;
    private final AgentToolExecutionAuthorizer authorizer;

    public KnowledgeIndexOperationsController(
            KnowledgeIndexOutboxRepository repository,
            KnowledgeIndexOutboxWorker worker,
            KnowledgeIndexRebuildService rebuildService,
            AgentToolExecutionAuthorizer authorizer
    ) {
        this.repository = repository;
        this.worker = worker;
        this.rebuildService = rebuildService;
        this.authorizer = authorizer;
    }

    @GetMapping("/outbox")
    public ApiResponse<KnowledgeIndexOutboxStatistics> statistics(
            @PathVariable @Positive Long workspaceId,
            @RequestHeader(name = "X-Demo-User-Id", defaultValue = "1") @Positive Long ownerUserId
    ) {
        authorize(ownerUserId, workspaceId);
        return ApiResponse.success(repository.statistics(workspaceId));
    }

    @PostMapping("/outbox/process-once")
    public ApiResponse<Map<String, Integer>> processOnce(
            @PathVariable @Positive Long workspaceId,
            @RequestHeader(name = "X-Demo-User-Id", defaultValue = "1") @Positive Long ownerUserId
    ) {
        authorize(ownerUserId, workspaceId);
        return ApiResponse.success(Map.of("claimed", worker.processOnce(workspaceId)));
    }

    @PostMapping("/rebuild")
    public ApiResponse<KnowledgeIndexRebuildResult> rebuild(
            @PathVariable @Positive Long workspaceId,
            @RequestHeader(name = "X-Demo-User-Id", defaultValue = "1") @Positive Long ownerUserId
    ) {
        authorize(ownerUserId, workspaceId);
        return ApiResponse.success(rebuildService.rebuild(workspaceId));
    }

    private void authorize(Long ownerUserId, Long workspaceId) {
        authorizer.authorize(new AgentToolExecutionContext(ownerUserId, workspaceId, null, null));
    }
}
