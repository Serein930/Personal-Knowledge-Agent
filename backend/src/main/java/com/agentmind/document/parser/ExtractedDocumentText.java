package com.agentmind.document.parser;

/**
 * 从原始文档中提取出的规范化文本。
 *
 * <p>解析层返回可直接进入切分流程的文本，并尽量保留后续检索增强生成需要的结构信息。
 * 当前阶段只暴露标题和纯文本，便携式文档 与办公文档解析接入后再补充更丰富的元数据。</p>
 */
public record ExtractedDocumentText(
        String title,
        String text
) {
}
