package com.agentmind.document.parser;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentmind.document.model.DocumentSourceType;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentTextExtractionServiceTests {

    @Test
    void extractShouldRejectMissingParserInsteadOfReturningEmptyText() {
        DocumentTextExtractionService service = new DocumentTextExtractionService(List.of());

        assertThatThrownBy(() -> service.extract(
                DocumentSourceType.PDF,
                "unknown.pdf",
                "content".getBytes(StandardCharsets.UTF_8)
        )).isInstanceOf(DocumentTextExtractionException.class)
                .hasMessageContaining("没有可处理");
    }

    @Test
    void extractShouldRejectEmptyResultFromParser() {
        DocumentTextExtractor emptyExtractor = new DocumentTextExtractor() {
            @Override
            public boolean supports(DocumentSourceType sourceType, String filename) {
                return true;
            }

            @Override
            public ExtractedDocumentText extract(byte[] content, String sourceName) {
                return new ExtractedDocumentText(sourceName, " ");
            }
        };
        DocumentTextExtractionService service = new DocumentTextExtractionService(List.of(emptyExtractor));

        assertThatThrownBy(() -> service.extract(
                DocumentSourceType.TEXT,
                "empty.txt",
                "content".getBytes(StandardCharsets.UTF_8)
        )).isInstanceOf(DocumentTextExtractionException.class)
                .hasMessageContaining("未提取到可索引文本");
    }
}
