package com.agentmind.agent.tool.model;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 工具执行完成后的标准结果。
 *
 * <p>result 仅返回给当前调用方；审计记录只保存 resultSummary，避免把完整私有知识内容重复写入审计存储。</p>
 */
public record AgentToolExecutionResult(
        JsonNode result,
        String resultSummary
) {
}
