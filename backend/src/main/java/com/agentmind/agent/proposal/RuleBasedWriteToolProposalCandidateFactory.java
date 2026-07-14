package com.agentmind.agent.proposal;

import com.agentmind.agent.proposal.model.WriteToolProposalCandidate;
import com.agentmind.agent.tool.CreateFlashcardAgentTool;
import com.agentmind.agent.tool.CreateNoteAgentTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 可重复的本地规则候选工厂。
 *
 * <p>该工厂既服务默认开发模式，也作为真实模型输出失败时的可选降级路径。它只负责生成安全候选，
 * 不创建确认单，更不会执行写工具。</p>
 */
@Component
public class RuleBasedWriteToolProposalCandidateFactory {

    private final ObjectMapper objectMapper;

    public RuleBasedWriteToolProposalCandidateFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<WriteToolProposalCandidate> create(String userQuestion, String generatedAnswer) {
        if (!StringUtils.hasText(generatedAnswer)) {
            return List.of();
        }
        if (containsAny(userQuestion, "复习卡片", "闪卡", "记忆卡片")) {
            ObjectNode arguments = objectMapper.createObjectNode()
                    .put("question", truncate(userQuestion, 500))
                    .put("answer", truncate(generatedAnswer, 10_000))
                    .put("explanation", "根据本次知识库回答生成，确认后保存到当前知识空间。");
            return List.of(new WriteToolProposalCandidate(CreateFlashcardAgentTool.TOOL_NAME, arguments));
        }
        if (containsAny(userQuestion, "创建笔记", "保存笔记", "整理成笔记", "记录成笔记")) {
            ObjectNode arguments = objectMapper.createObjectNode()
                    .put("title", truncate(userQuestion, 120))
                    .put("content", truncate(generatedAnswer, 20_000));
            return List.of(new WriteToolProposalCandidate(CreateNoteAgentTool.TOOL_NAME, arguments));
        }
        return List.of();
    }

    private boolean containsAny(String value, String... keywords) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String truncate(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}
