package com.agentmind.chat.service;

import com.agentmind.chat.config.RagAnswerGenerationProperties;
import com.agentmind.chat.model.dto.RagCitationResponse;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 检索增强生成低置信度拒答策略。
 *
 * <p>当前策略只使用检索结果数量和最高相似度分数，规则足够透明，也方便后续加入
 * 引用覆盖率、重排分数、用户反馈等更细的质量信号。</p>
 */
@Component
public class RagRefusalPolicy {

    private final RagAnswerGenerationProperties properties;

    public RagRefusalPolicy(RagAnswerGenerationProperties properties) {
        this.properties = properties;
    }

    public RagRefusalDecision decide(List<RagCitationResponse> citations) {
        if (citations.isEmpty()) {
            return new RagRefusalDecision(true, "当前知识库没有检索到可用于回答该问题的资料。");
        }

        double bestScore = citations.stream()
                .max(Comparator.comparingDouble(RagCitationResponse::score))
                .map(RagCitationResponse::score)
                .orElse(0D);
        if (bestScore < properties.getMinimumCitationScore()) {
            return new RagRefusalDecision(true, "当前检索结果相关性较低，暂时不能基于已有资料可靠回答。");
        }

        return new RagRefusalDecision(false, "");
    }
}
