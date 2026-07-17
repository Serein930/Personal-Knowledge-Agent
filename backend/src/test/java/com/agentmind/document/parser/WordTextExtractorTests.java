package com.agentmind.document.parser;

import static com.agentmind.document.testfixture.DocumentBinaryTestFixtures.docx;
import static com.agentmind.document.testfixture.DocumentBinaryTestFixtures.wordCompatibleDoc;
import static org.assertj.core.api.Assertions.assertThat;

import com.agentmind.document.model.DocumentSourceType;
import java.util.List;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.junit.jupiter.api.Test;

class WordTextExtractorTests {

    private final WordTextExtractor extractor = new WordTextExtractor(properties());

    @Test
    void extractShouldReadDocxTitleAndParagraphs() {
        ExtractedDocumentText result = extractor.extract(
                docx("RAG Notes", List.of(
                        "Retrieval keeps answers grounded.",
                        "Citations make the result traceable."
                )),
                "rag-notes.docx"
        );

        assertThat(result.title()).isEqualTo("RAG Notes");
        assertThat(result.text()).contains("Retrieval keeps answers grounded");
        assertThat(result.text()).contains("Citations make the result traceable");
    }

    @Test
    void extractShouldDetectWordCompatibleDocByContent() {
        ExtractedDocumentText result = extractor.extract(
                wordCompatibleDoc("Legacy DOC content remains searchable."),
                "legacy.doc"
        );

        assertThat(result.title()).isEqualTo("legacy.doc");
        assertThat(result.text()).contains("Legacy DOC content remains searchable");
        assertThat(extractor.supports(DocumentSourceType.WORD, "legacy.doc")).isTrue();
        assertThat(extractor.supports(DocumentSourceType.WORD, "modern.docx")).isTrue();
    }

    @Test
    void parserRegistryShouldLoadLegacyBinaryDocParser() {
        // 旧版 DOC 使用 OLE2 二进制容器。这里直接检查 Tika 解析器注册表，
        // 可以尽早发现部署包误删 Office 解析模块、导致 .doc 只能通过上传校验却无法解析的问题。
        AutoDetectParser parser = new AutoDetectParser();

        assertThat(parser.getSupportedTypes(new ParseContext()))
                .contains(MediaType.parse("application/msword"));
    }

    private DocumentParsingProperties properties() {
        DocumentParsingProperties properties = new DocumentParsingProperties();
        properties.setMaxExtractedCharacters(10_000);
        return properties;
    }
}
