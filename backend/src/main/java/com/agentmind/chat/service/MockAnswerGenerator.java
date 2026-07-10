package com.agentmind.chat.service;

import com.agentmind.chat.model.dto.RagCitationResponse;
import com.agentmind.chat.model.dto.TokenUsageResponse;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Deterministic answer generator used before Spring AI is connected.
 *
 * <p>The implementation does not pretend to be a language model. It summarizes the top retrieved chunks and attaches
 * citation numbers, which is enough for frontend integration and for verifying that answers are grounded in
 * retrieved private knowledge.</p>
 */
@Component
public class MockAnswerGenerator implements AnswerGenerator {

    @Override
    public GeneratedAnswer generate(AnswerGenerationRequest request) {
        if (request.citations().isEmpty()) {
            return new GeneratedAnswer(
                    "The current knowledge base does not contain enough retrieved context to answer this question.",
                    new TokenUsageResponse(0, 0, 0)
            );
        }

        String citedSummary = request.citations().stream()
                .limit(3)
                .map(this::toCitedSentence)
                .collect(Collectors.joining(" "));
        String answer = "Based on the retrieved knowledge, " + citedSummary
                + " This is a mock answer generated from retrieved context only; a Spring AI model adapter can replace it later.";
        return new GeneratedAnswer(answer, new TokenUsageResponse(0, 0, 0));
    }

    private String toCitedSentence(RagCitationResponse citation) {
        String excerpt = citation.excerpt();
        String trimmed = excerpt.length() > 180 ? excerpt.substring(0, 180) + "..." : excerpt;
        return trimmed + " [" + citation.index() + "]";
    }
}
