package com.agentmind.document.parser;

import com.agentmind.document.model.DocumentSourceType;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.xml.sax.SAXException;

/**
 * 使用 Apache Tika 与 POI 提取旧版 DOC 和 OOXML DOCX 文本。
 *
 * <p>Tika 根据文件签名选择 OLE2 或 OOXML 解析器，不依赖客户端声明的 MIME 类型。
 * 正文处理器设置字符写入上限，避免 Office 压缩内容解包后无限扩张。</p>
 */
@Component
public class WordTextExtractor implements DocumentTextExtractor {

    private static final Logger LOGGER = LoggerFactory.getLogger(WordTextExtractor.class);

    private final DocumentParsingProperties properties;

    public WordTextExtractor(DocumentParsingProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean supports(DocumentSourceType sourceType, String filename) {
        if (sourceType != DocumentSourceType.WORD || filename == null) {
            return false;
        }
        String lowerName = filename.toLowerCase(Locale.ROOT);
        return lowerName.endsWith(".doc") || lowerName.endsWith(".docx");
    }

    @Override
    public ExtractedDocumentText extract(byte[] content, String sourceName) {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, sourceName);
        BodyContentHandler handler = new BodyContentHandler(properties.getMaxExtractedCharacters());
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(content)) {
            new AutoDetectParser().parse(inputStream, handler, metadata, new ParseContext());
            String normalizedText = DocumentTextNormalizer.normalize(handler.toString());
            if (!StringUtils.hasText(normalizedText)) {
                throw new DocumentTextExtractionException("Word 文档未提取到可索引文本");
            }
            return new ExtractedDocumentText(
                    DocumentTextNormalizer.titleOrFallback(metadata.get(TikaCoreProperties.TITLE), sourceName),
                    normalizedText
            );
        } catch (DocumentTextExtractionException exception) {
            throw exception;
        } catch (IOException | SAXException | TikaException | RuntimeException exception) {
            LOGGER.warn("Word 文本解析失败，文件名={}", sourceName, exception);
            throw new DocumentTextExtractionException("Word 文件损坏、加密、超限或格式不受支持", exception);
        }
    }
}
