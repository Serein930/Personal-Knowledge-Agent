package com.agentmind.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 工具成功结果的短期内存缓存。
 *
 * <p>审计表只保存脱敏摘要，避免重复存储完整私有知识片段。缓存仅用于同一进程内相同请求编号的幂等响应；
 * 服务重启后仍以审计记录为准，但不会重新暴露已缓存的完整结果。</p>
 */
@Component
public class InMemoryAgentToolResultCache {

    private final ConcurrentHashMap<Long, JsonNode> results = new ConcurrentHashMap<>();

    public void put(Long auditId, JsonNode result) {
        if (auditId != null && result != null) {
            results.put(auditId, result.deepCopy());
        }
    }

    public Optional<JsonNode> get(Long auditId) {
        JsonNode result = results.get(auditId);
        return result == null ? Optional.empty() : Optional.of(result.deepCopy());
    }
}
