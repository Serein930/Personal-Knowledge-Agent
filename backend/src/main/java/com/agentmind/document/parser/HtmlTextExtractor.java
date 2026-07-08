package com.agentmind.document.parser;

import com.agentmind.document.model.DocumentSourceType;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Lightweight HTML text extractor.
 *
 * <p>This is a Stage 4.5 skeleton rather than a full readability implementation. It removes scripts, styles and
 * tags, extracts a basic title, and decodes common HTML entities. Jsoup/readability can replace this class later
 * when web article quality becomes the focus.</p>
 */
@Component
public class HtmlTextExtractor implements DocumentTextExtractor {

    private static final Pattern TITLE_PATTERN = Pattern.compile("(?is)<title[^>]*>(.*?)</title>");

    @Override
    public boolean supports(DocumentSourceType sourceType, String filename) {
        return sourceType == DocumentSourceType.WEB_PAGE;
    }

    @Override
    public ExtractedDocumentText extract(byte[] content, String sourceName) {
        String html = decodeUtf8(content);
        String title = extractTitle(html, sourceName);
        String text = html
                .replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?is)<noscript[^>]*>.*?</noscript>", " ")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p\\s*>", "\n")
                .replaceAll("(?i)</h[1-6]\\s*>", "\n")
                .replaceAll("(?is)<[^>]+>", " ");
        return new ExtractedDocumentText(title, normalizeHtmlEntities(text));
    }

    private String extractTitle(String html, String fallback) {
        Matcher matcher = TITLE_PATTERN.matcher(html);
        if (!matcher.find()) {
            return fallback;
        }
        String title = normalizeHtmlEntities(matcher.group(1));
        return StringUtils.hasText(title) ? title : fallback;
    }

    private String normalizeHtmlEntities(String value) {
        return value
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}
