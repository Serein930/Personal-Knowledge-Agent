package com.agentmind.document.parser;

import com.agentmind.document.model.DocumentSourceType;
import org.springframework.stereotype.Component;

/**
 * Extracts UTF-8 text and source code files.
 *
 * <p>No syntax-specific parsing is done yet. Keeping the extractor simple gives the project a reliable baseline
 * before language-aware code chunking is added.</p>
 */
@Component
public class PlainTextExtractor implements DocumentTextExtractor {

    @Override
    public boolean supports(DocumentSourceType sourceType, String filename) {
        return sourceType == DocumentSourceType.TEXT || sourceType == DocumentSourceType.CODE;
    }

    @Override
    public ExtractedDocumentText extract(byte[] content, String sourceName) {
        String text = decodeUtf8(content)
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .trim();
        return new ExtractedDocumentText(sourceName, text);
    }
}
