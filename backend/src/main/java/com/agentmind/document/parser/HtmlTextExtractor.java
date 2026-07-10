package com.agentmind.document.parser;

import com.agentmind.document.model.DocumentSourceType;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 轻量网页文本提取器。
 *
 * <p>当前实现还不是完整正文提取方案，只负责移除脚本、样式和标签，提取基础标题，并处理常见实体。
 * 当网页文章质量成为重点时，可以再替换为网页解析库和正文提取实现。</p>
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
