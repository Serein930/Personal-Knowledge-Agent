package com.agentmind.ingestion.web;

import java.io.IOException;
import java.net.URI;

/**
 * Fetches raw HTML for a validated public URL.
 */
public interface HtmlFetchService {

    HtmlFetchResult fetch(URI uri) throws IOException, InterruptedException;
}
