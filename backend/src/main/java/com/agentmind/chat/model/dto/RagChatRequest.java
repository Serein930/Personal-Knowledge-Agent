package com.agentmind.chat.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Request contract for RAG chat.
 *
 * <p>The current Stage 6 preparation only performs retrieval and context assembly. Later, the same request can be
 * passed to a Spring AI chat model adapter after authorization, memory and prompt versioning are added.</p>
 */
public record RagChatRequest(
        Long conversationId,

        @NotBlank(message = "question must not be blank")
        String question,

        @Min(value = 1, message = "topK must be at least 1")
        @Max(value = 20, message = "topK must not be greater than 20")
        Integer topK,

        @Valid
        RagChatFilterRequest filters
) {
}
