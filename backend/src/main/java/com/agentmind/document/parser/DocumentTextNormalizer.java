package com.agentmind.document.parser;

import org.springframework.util.StringUtils;

/** 文档解析器共用的换行、空白和标题规范化工具。 */
final class DocumentTextNormalizer {

    private DocumentTextNormalizer() {
    }

    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace('\u0000', ' ')
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[ \\t\\x0B\\f]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    static String titleOrFallback(String title, String fallback) {
        return StringUtils.hasText(title) ? normalize(title) : fallback;
    }
}
