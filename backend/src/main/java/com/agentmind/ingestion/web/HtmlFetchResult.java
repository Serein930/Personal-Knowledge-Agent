package com.agentmind.ingestion.web;

import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * 原始网页内容抓取结果。
 *
 * <p>该结果会作为后续正文提取和文本切分的输入。</p>
 */
public record HtmlFetchResult(
        URI uri,
        int statusCode,
        String contentType,
        String html
) {

    public long byteSize() {
        return html == null ? 0 : html.getBytes(StandardCharsets.UTF_8).length;
    }
}
