package com.agentmind.chat.model.dto;

import com.agentmind.document.model.DocumentSourceType;
import java.util.List;
import jakarta.validation.constraints.Size;

/**
 * 检索增强生成问答的可选检索过滤条件。
 *
 * <p>当前阶段只实现按知识空间隔离的向量检索，标签和来源类型先作为接口契约保留。
 * 后续接入文档元数据持久化后再真正参与过滤。</p>
 */
public record RagChatFilterRequest(
        List<String> tags,
        List<DocumentSourceType> sourceTypes,

        @Size(max = 20, message = "单次问答最多选择 20 个文档")
        List<Long> documentIds
) {
    /** 兼容尚未指定文档范围的既有调用方。 */
    public RagChatFilterRequest(List<String> tags, List<DocumentSourceType> sourceTypes) {
        this(tags, sourceTypes, List.of());
    }
}
