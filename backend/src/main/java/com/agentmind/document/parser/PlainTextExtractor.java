package com.agentmind.document.parser;

import com.agentmind.document.model.DocumentSourceType;
import org.springframework.stereotype.Component;

/**
 * 提取纯文本和源代码文件。
 *
 * <p>当前阶段暂不做语言语法级解析。先保持提取器简单可靠，后续再加入按编程语言感知的代码切分能力。</p>
 */
@Component
public class PlainTextExtractor implements DocumentTextExtractor {

    @Override
    public boolean supports(DocumentSourceType sourceType, String filename) {
        return sourceType == DocumentSourceType.TEXT || sourceType == DocumentSourceType.CODE;
    }

    @Override
    public ExtractedDocumentText extract(byte[] content, String sourceName) {
        String text = decodeUtf8(content)
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .trim();
        return new ExtractedDocumentText(sourceName, text);
    }
}
