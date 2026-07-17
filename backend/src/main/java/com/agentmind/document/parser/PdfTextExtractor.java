package com.agentmind.document.parser;

import com.agentmind.document.model.DocumentSourceType;
import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 使用 PDFBox 提取 PDF 标题与可选择文本。
 *
 * <p>当前实现面向包含文本层的 PDF。扫描图片不会被误判为成功，而是返回需要 OCR 的明确错误。
 * 页数和字符数都受配置限制，为后续增加分页引用、页眉页脚去重保留独立扩展位置。</p>
 */
@Component
public class PdfTextExtractor implements DocumentTextExtractor {

    private static final Logger LOGGER = LoggerFactory.getLogger(PdfTextExtractor.class);

    private final DocumentParsingProperties properties;

    public PdfTextExtractor(DocumentParsingProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean supports(DocumentSourceType sourceType, String filename) {
        return sourceType == DocumentSourceType.PDF;
    }

    @Override
    public ExtractedDocumentText extract(byte[] content, String sourceName) {
        try (PDDocument document = Loader.loadPDF(content)) {
            validatePageCount(document.getNumberOfPages());
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String extractedText = stripper.getText(document);
            validateCharacterCount(extractedText);
            String normalizedText = DocumentTextNormalizer.normalize(extractedText);
            if (!StringUtils.hasText(normalizedText)) {
                throw new DocumentTextExtractionException(
                        "PDF 未提取到可索引文本，扫描版 PDF 需要 OCR 支持");
            }
            String metadataTitle = document.getDocumentInformation() == null
                    ? null
                    : document.getDocumentInformation().getTitle();
            return new ExtractedDocumentText(
                    DocumentTextNormalizer.titleOrFallback(metadataTitle, sourceName),
                    normalizedText
            );
        } catch (DocumentTextExtractionException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("PDF 文本解析失败，文件名={}", sourceName, exception);
            throw new DocumentTextExtractionException("PDF 文件损坏、加密或格式不受支持", exception);
        }
    }

    private void validatePageCount(int pageCount) {
        if (pageCount > properties.getMaxPdfPages()) {
            throw new DocumentTextExtractionException(
                    "PDF 页数超过解析限制：" + properties.getMaxPdfPages());
        }
    }

    private void validateCharacterCount(String text) {
        if (text != null && text.length() > properties.getMaxExtractedCharacters()) {
            throw new DocumentTextExtractionException(
                    "PDF 文本超过解析字符限制：" + properties.getMaxExtractedCharacters());
        }
    }
}
