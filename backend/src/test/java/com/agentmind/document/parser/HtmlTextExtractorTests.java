package com.agentmind.document.parser;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class HtmlTextExtractorTests {

    private final HtmlTextExtractor extractor = new HtmlTextExtractor();

    @Test
    void extractShouldRemoveScriptsAndTags() {
        ExtractedDocumentText result = extractor.extract("""
                <html>
                  <head><title>Sample &amp; Test</title><script>bad()</script></head>
                  <body><h1>Hello</h1><p>Readable text&nbsp;here.</p></body>
                </html>
                """.getBytes(StandardCharsets.UTF_8), "fallback");

        assertThat(result.title()).isEqualTo("Sample & Test");
        assertThat(result.text()).contains("Hello");
        assertThat(result.text()).contains("Readable text here.");
        assertThat(result.text()).doesNotContain("bad()");
        assertThat(result.text()).doesNotContain("<p>");
    }
}
