package com.agentmind.health.controller;

import com.agentmind.common.response.ApiResponse;
import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 应用健康检查接口。
 *
 * <p>该接口用于前端联调、部署探活和基础可用性验证。它不依赖数据库或外部 AI 服务，
 * 因此适合在 Stage 1 作为最小后端验收接口。</p>
 */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private final String applicationName;

    public HealthController(@Value("${spring.application.name}") String applicationName) {
        this.applicationName = applicationName;
    }

    @GetMapping
    public ApiResponse<HealthResponse> health() {
        return ApiResponse.success(new HealthResponse(applicationName, "UP", OffsetDateTime.now()));
    }

    public record HealthResponse(
            String application,
            String status,
            OffsetDateTime checkedAt
    ) {
    }
}
