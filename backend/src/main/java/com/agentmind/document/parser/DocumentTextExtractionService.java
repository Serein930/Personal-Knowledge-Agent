package com.agentmind.document.parser;

import com.agentmind.document.model.DocumentSourceType;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 根据文档来源类型选择文本解析器。
 *
 * <p>暂不支持解析的格式会返回空文本，而不是让整个上传流程失败。这样当前阶段可以先保存
 * 便携式文档和办公文档原始文件，同时清楚标记解析能力还需要后续补齐。</p>
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
