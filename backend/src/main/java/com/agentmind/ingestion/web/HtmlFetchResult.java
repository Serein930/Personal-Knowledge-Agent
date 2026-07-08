package com.agentmind.ingestion.web;

import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Raw HTML fetch result used as the input for later readability extraction and chunking.
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
