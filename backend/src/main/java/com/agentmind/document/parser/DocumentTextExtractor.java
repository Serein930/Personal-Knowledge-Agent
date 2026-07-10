package com.agentmind.document.parser;

import com.agentmind.document.model.DocumentSourceType;
import java.nio.charset.StandardCharsets;

/**
 * 文档文本提取策略接口。
 *
 * <p>每个实现只关注一种内容类型，方便替换解析规则。网页解析当前只需要轻量去标签，
 * 后续可以替换为网页解析库和正文提取方案，而不需要改摄取服务。</p>
 */
public interface DocumentTextExtractor {

    boolean supports(DocumentSourceType sourceType, String filename);

    ExtractedDocumentText extract(byte[] content, String sourceName);

    default String decodeUtf8(byte[] content) {
        return new String(content, StandardCharsets.UTF_8);
    }
}
