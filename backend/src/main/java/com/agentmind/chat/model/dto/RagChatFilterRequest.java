package com.agentmind.chat.model.dto;

import com.agentmind.document.model.DocumentSourceType;
import java.util.List;

/**
 * RAG 问答检索过滤条件。
 *
 * <p>过滤条件用于限制检索范围，例如只查某些标签或某类文档。
 * workspaceId 不放在该对象里，因为它来自接口路径并且必须由后端鉴权校验。</p>
 */
public record RagChatFilterRequest(
        List<String> tags,
        List<DocumentSourceType> sourceTypes
) {
}
