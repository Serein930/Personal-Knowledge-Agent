package com.agentmind.agent.proposal.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Spring AI 结构化输出目标。
 *
 * <p>字段保持扁平，便于不同模型稳定生成 JSON。服务端会根据 {@code toolName} 选择所需字段，
 * 忽略不属于目标工具的内容，并在创建确认单前再次执行长度与必填校验。</p>
 */
public record StructuredWriteToolProposalDecision(
        @JsonPropertyDescription("是否需要向用户提出写入建议") boolean proposalRequired,
        @JsonPropertyDescription("只能是 note.create 或 flashcard.create") String toolName,
        @JsonPropertyDescription("笔记标题，仅 note.create 使用") String title,
        @JsonPropertyDescription("笔记正文，仅 note.create 使用") String content,
        @JsonPropertyDescription("复习卡片问题，仅 flashcard.create 使用") String question,
        @JsonPropertyDescription("复习卡片答案，仅 flashcard.create 使用") String answer,
        @JsonPropertyDescription("复习卡片补充解释，可为空") String explanation
) {
}
