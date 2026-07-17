package com.agentmind.document.parser;

import static com.agentmind.document.testfixture.DocumentBinaryTestFixtures.pdf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class PdfTextExtractorTests {

    @Test
    void extractShouldReadMetadataTitleAndPageText() {
        PdfTextExtractor extractor = new PdfTextExtractor(properties(10, 10_000));

        ExtractedDocumentText result = extractor.extract(
                pdf("Java Concurrency", List.of(
                        "Thread pools reuse worker threads.",
                        "Queue policies limit concurrent workload."
                )),
                "concurrency.pdf"
        );

        assertThat(result.title()).isEqualTo("Java Concurrency");
        assertThat(result.text()).contains("Thread pools reuse worker threads");
        assertThat(result.text()).contains("Queue policies limit concurrent workload");
    }

    @Test
    void extractShouldRejectDocumentBeyondPageLimit() {
        PdfTextExtractor extractor = new PdfTextExtractor(properties(1, 10_000));

        assertThatThrownBy(() -> extractor.extract(
                pdf("Too many pages", List.of("page one", "page two")),
                "too-many-pages.pdf"
        )).isInstanceOf(DocumentTextExtractionException.class)
                .hasMessageContaining("页数超过解析限制");
    }

    @Test
    void extractShouldRejectPdfWithoutTextLayer() {
        PdfTextExtractor extractor = new PdfTextExtractor(properties(10, 10_000));

        assertThatThrownBy(() -> extractor.extract(
                pdf("Scanned", List.of("")),
                "scanned.pdf"
        )).isInstanceOf(DocumentTextExtractionException.class)
                .hasMessageContaining("需要 OCR");
    }

    private DocumentParsingProperties properties(int maxPages, int maxCharacters) {
        DocumentParsingProperties properties = new DocumentParsingProperties();
        properties.setMaxPdfPages(maxPages);
        properties.setMaxExtractedCharacters(maxCharacters);
        return properties;
    }
}
