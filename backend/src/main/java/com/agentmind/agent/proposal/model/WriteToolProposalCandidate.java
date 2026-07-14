package com.agentmind.agent.proposal.model;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 通过安全校验、可以转换为确认单的写工具建议候选。
 *
 * <p>模型原始文本不能直接进入工具执行链路。候选对象只允许携带服务端重新组装后的工具名称和参数，
 * 从而阻断模型伪造工具名称、越权参数或确认状态。</p>
 */
public record WriteToolProposalCandidate(String toolName, ObjectNode arguments) {
}
