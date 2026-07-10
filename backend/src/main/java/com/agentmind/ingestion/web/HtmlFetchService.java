package com.agentmind.ingestion.web;

import java.io.IOException;
import java.net.URI;

/**
 * 根据已校验的公开链接抓取原始网页内容。
 */
public interface HtmlFetchService {

    HtmlFetchResult fetch(URI uri) throws IOException, InterruptedException;
}
