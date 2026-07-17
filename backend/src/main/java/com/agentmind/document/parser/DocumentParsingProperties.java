package com.agentmind.document.parser;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 文件文本解析的资源保护配置。
 *
 * <p>上传字节大小只能限制原始文件，无法限制 PDF 页数或 Office 解压后的文本规模。
 * 解析器必须同时执行页数和字符数约束，避免恶意或异常文档长时间占用内存与工作线程。</p>
 */
@Validated
@Component
@ConfigurationProperties(prefix = "agentmind.ingestion.parsing")
public class DocumentParsingProperties {

    @Min(1)
    private int maxPdfPages = 500;

    @Min(1)
    private int maxExtractedCharacters = 2_000_000;

    public int getMaxPdfPages() {
        return maxPdfPages;
    }

    public void setMaxPdfPages(int maxPdfPages) {
        this.maxPdfPages = maxPdfPages;
    }

    public int getMaxExtractedCharacters() {
        return maxExtractedCharacters;
    }

    public void setMaxExtractedCharacters(int maxExtractedCharacters) {
        this.maxExtractedCharacters = maxExtractedCharacters;
    }
}
