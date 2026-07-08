package com.agentmind.document.parser;

import com.agentmind.document.model.DocumentSourceType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Extracts Markdown content while preserving headings.
 *
 * <p>Headings are intentionally kept because the chunker uses them to build heading paths. Inline formatting is
 * lightly normalized, but code blocks are preserved because technical notes often depend on exact code snippets.</p>
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
