package com.agentmind.document.parser;

import com.agentmind.document.model.DocumentSourceType;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Selects a parser for the current document source type.
 *
 * <p>Unsupported formats return an empty extraction result instead of failing the whole upload. This lets PDF/Word
 * files be stored now while making it obvious that parsing support still needs to be implemented.</p>
 */
@Service
public class DocumentTextExtractionService {

    private final List<DocumentTextExtractor> extractors;

    public DocumentTextExtractionService(List<DocumentTextExtractor> extractors) {
        this.extractors = extractors;
    }

    public ExtractedDocumentText extract(DocumentSourceType sourceType, String sourceName, byte[] content) {
        return extractors.stream()
                .filter(extractor -> extractor.supports(sourceType, sourceName))
                .findFirst()
                .map(extractor -> extractor.extract(content, sourceName))
                .orElseGet(() -> new ExtractedDocumentText(sourceName, ""));
    }
}
