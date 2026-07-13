package com.agentmind.chat.service;

import com.agentmind.chat.config.RagAnswerGenerationProperties;
import com.agentmind.chat.memory.model.ChatMessageRole;
import com.agentmind.chat.memory.service.ChatMemoryEntry;
import com.agentmind.chat.model.dto.RagCitationResponse;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * 检索增强生成提示词模板。
 *
 * <p>提示词模板集中维护检索上下文格式和回答生成指令。后续做提示词版本对比、检索增强生成评估或
 * 真实聊天模型接入时，只需要替换或扩展这里，而不需要改控制层。</p>
 */
@Component
public class RagPromptTemplate {

    private final RagAnswerGenerationProperties properties;

    public RagPromptTemplate(RagAnswerGenerationProperties properties) {
        this.properties = properties;
    }

    public String promptVersion() {
        return properties.getPromptVersion();
    }

    public String buildPromptContext(
            String question,
            List<RagCitationResponse> citations,
            List<ChatMemoryEntry> conversationHistory
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("请只根据下面检索到的个人知识片段回答问题。\n");
        builder.append("如果资料不足或相关性不够，请明确说明当前知识库没有足够依据。\n\n");
        appendConversationHistory(builder, conversationHistory);
        builder.append("用户问题：\n").append(question).append("\n\n");
        builder.append("检索上下文：\n");
        citations.stream()
                .limit(properties.getMaxContextCitations())
                .forEach(citation -> appendCitation(builder, citation));
        return builder.toString().trim();
    }

    private void appendConversationHistory(StringBuilder builder, List<ChatMemoryEntry> conversationHistory) {
        if (conversationHistory.isEmpty()) {
            return;
        }
        builder.append("短期会话上下文（仅用于理解当前问题，不作为知识事实来源）：\n");
        conversationHistory.forEach(entry -> builder
                .append(entry.role() == ChatMessageRole.USER ? "用户：" : "助手：")
                .append(entry.content())
                .append("\n"));
        builder.append("\n");
    }

    public String buildGenerationPrompt(String question, String promptContext, RagRefusalDecision refusalDecision) {
        StringBuilder builder = new StringBuilder();
        builder.append("提示词版本：").append(properties.getPromptVersion()).append("\n");
        builder.append("回答要求：\n");
        builder.append("1. 只使用检索上下文中的信息。\n");
        builder.append("2. 每个关键结论后尽量附上引用编号，例如 [1]。\n");
        builder.append("3. 不要编造上下文之外的事实。\n");
        builder.append("4. 如果拒答判断为真，直接说明资料不足原因。\n\n");
        builder.append("拒答判断：").append(refusalDecision.shouldRefuse() ? "是" : "否").append("\n");
        if (refusalDecision.shouldRefuse()) {
            builder.append("拒答原因：").append(refusalDecision.reason()).append("\n");
        }
        builder.append("\n").append(promptContext).append("\n\n");
        builder.append("请回答：").append(question);
        return builder.toString();
    }

    private void appendCitation(StringBuilder builder, RagCitationResponse citation) {
        builder.append("[")
                .append(citation.index())
                .append("] 文档ID=")
                .append(citation.documentId())
                .append("，片段ID=")
                .append(citation.chunkId())
                .append("，标题路径=")
                .append(citation.headingPath() == null ? "" : citation.headingPath())
                .append("，相关性=")
                .append(String.format(Locale.ROOT, "%.4f", citation.score()))
                .append("\n")
                .append(citation.excerpt())
                .append("\n\n");
    }
}
