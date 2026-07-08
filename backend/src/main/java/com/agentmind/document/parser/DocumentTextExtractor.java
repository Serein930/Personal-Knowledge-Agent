package com.agentmind.document.parser;

import com.agentmind.document.model.DocumentSourceType;
import java.nio.charset.StandardCharsets;

/**
 * Strategy interface for extracting text from one document source type.
 *
 * <p>Each implementation focuses on one content family, which keeps parser rules replaceable. This matters for
 * web pages because a simple HTML stripper is enough for Stage 4.5, but can later be replaced by Jsoup plus a
 * readability extractor without changing the ingestion service.</p>
 */
public interface DocumentTextExtractor {

    boolean supports(DocumentSourceType sourceType, String filename);

    ExtractedDocumentText extract(byte[] content, String sourceName);

    default String decodeUtf8(byte[] content) {
        return new String(content, StandardCharsets.UTF_8);
    }
}
