package com.agentmind.document.parser;

import com.agentmind.document.model.DocumentSourceType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 提取标记文档内容并保留标题。
 *
 * <p>标题会被保留下来，因为切分器会使用标题构造标题路径。行内格式只做轻量规范化，
 * 代码块会保留原样，因为技术笔记通常依赖准确的代码片段。</p>
 */
@Component
public class MarkdownTextExtractor implements DocumentTextExtractor {

    @Override
    public boolean supports(DocumentSourceType sourceType, String filename) {
        return sourceType == DocumentSourceType.MARKDOWN;
    }

    @Override
    public ExtractedDocumentText extract(byte[] content, String sourceName) {
        String text = decodeUtf8(content)
                .replace("\r\n", "\n")
                .replace("\r", "\n");
        String title = firstMarkdownHeading(text, sourceName);
        return new ExtractedDocumentText(title, text.trim());
    }

    private String firstMarkdownHeading(String text, String fallback) {
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                String heading = trimmed.replaceFirst("^#{1,6}\\s*", "").trim();
                if (StringUtils.hasText(heading)) {
                    return heading;
                }
            }
        }
        return fallback;
    }
}
