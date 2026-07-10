package com.agentmind.chat.service;

import com.agentmind.chat.model.dto.TokenUsageResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 真实聊天模型回答生成适配器骨架。
 *
 * <p>当前阶段先保留适配层位置和配置开关，不直接接入真实模型依赖，也不要求配置 API Key。
 * 后续接入模型框架时，可以在这里注入聊天模型客户端，把生成提示词发送给模型，
 * 并把模型返回内容和令牌用量封装为回答生成结果。</p>
 */
@Component
@ConditionalOnProperty(prefix = "agentmind.rag", name = "answer-generator", havingValue = "spring-ai")
public class SpringAiChatModelAnswerGenerator implements AnswerGenerator {

    @Override
    public GeneratedAnswer generate(AnswerGenerationRequest request) {
        if (request.refusalDecision().shouldRefuse()) {
            return new GeneratedAnswer(request.refusalDecision().reason(), new TokenUsageResponse(0, 0, 0));
        }

        String answer = """
                当前已切换到真实聊天模型适配器骨架，但尚未接入真实模型客户端。
                已生成可发送给模型的提示词，后续接入真实模型后会在这里返回模型回答。
                """.strip();
        return new GeneratedAnswer(answer, new TokenUsageResponse(0, 0, 0));
    }
}
