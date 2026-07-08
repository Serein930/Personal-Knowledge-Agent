package com.agentmind.knowledge.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for temporary semantic search API.
 */
public record KnowledgeSearchRequest(
        @NotBlank(message = "query must not be blank")
        String query,

        @Min(value = 1, message = "topK must be at least 1")
        @Max(value = 20, message = "topK must not be greater than 20")
        Integer topK
) {
}
