package com.agentmind.document.parser;

import com.agentmind.document.model.DocumentSourceType;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 根据文档来源类型选择文本解析器。
 *
 * <p>服务只负责选择解析策略并校验统一结果。不存在解析器或正文为空时必须明确失败，
 * 禁止把零片段文档标记为摄取成功。</p>
 */
@Service
public class DocumentTextExtractionService {

    private final List<DocumentTextExtractor> extractors;

    public DocumentTextExtractionService(List<DocumentTextExtractor> extractors) {
        this.extractors = extractors;
    }

    public ExtractedDocumentText extract(DocumentSourceType sourceType, String sourceName, byte[] content) {
        if (sourceType == null || content == null || content.length == 0) {
            throw new DocumentTextExtractionException("文档类型或内容不能为空");
        }
        ExtractedDocumentText extracted = extractors.stream()
                .filter(extractor -> extractor.supports(sourceType, sourceName))
                .findFirst()
                .map(extractor -> extractor.extract(content, sourceName))
                .orElseThrow(() -> new DocumentTextExtractionException(
                        "当前没有可处理该文件类型的文本解析器：" + sourceType));
        if (extracted == null || !StringUtils.hasText(extracted.text())) {
            throw new DocumentTextExtractionException("文档未提取到可索引文本");
        }
        return extracted;
    }
}
