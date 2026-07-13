package com.agentmind.chat.service;

import com.agentmind.chat.model.dto.RagCitationResponse;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 模拟回答内容构造器。
 *
 * <p>同步生成器和流式生成器复用该组件，保证相同请求无论通过哪种接口访问，
 * 都能得到内容一致且可重复的本地模拟答案。</p>
 */
@Component
public class MockAnswerComposer {

    public String compose(AnswerGenerationRequest request) {
        if (request.refusalDecision().shouldRefuse()) {
            return request.refusalDecision().reason();
        }

        String citedSummary = request.citations().stream()
                .limit(3)
                .map(this::toCitedSentence)
                .collect(Collectors.joining(" "));
        return "根据当前知识库检索结果，可以得到以下回答：" + citedSummary
                + "。以上内容来自模拟生成器，仅用于验证检索增强生成链路；后续可以由真实模型适配器替换。";
    }

    private String toCitedSentence(RagCitationResponse citation) {
        String excerpt = citation.excerpt();
        String trimmed = excerpt.length() > 180 ? excerpt.substring(0, 180) + "..." : excerpt;
        return trimmed + " [" + citation.index() + "]";
    }
}
