package com.agentmind.study.flashcard.model.dto;

import jakarta.validation.constraints.Size;
import java.util.List;

/** 批量删除复习卡片；deleteAll 与 cardIds 二选一。 */
public record BulkDeleteFlashcardsRequest(
        @Size(max = 500, message = "单次最多删除500张卡片") List<Long> cardIds,
        boolean deleteAll
) {
    public BulkDeleteFlashcardsRequest {
        cardIds = cardIds == null ? List.of() : List.copyOf(cardIds);
    }
}
