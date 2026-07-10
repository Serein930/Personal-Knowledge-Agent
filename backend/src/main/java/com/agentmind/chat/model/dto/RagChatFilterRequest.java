package com.agentmind.chat.model.dto;

import com.agentmind.document.model.DocumentSourceType;
import java.util.List;

/**
 * Optional retrieval filters for RAG chat.
 *
 * <p>Filters are part of the API contract now, even though only workspace-scoped vector search is implemented in
 * this stage. Tags and source types will be applied when document metadata persistence is introduced.</p>
 */
public record RagChatFilterRequest(
        List<String> tags,
        List<DocumentSourceType> sourceTypes
) {
}
