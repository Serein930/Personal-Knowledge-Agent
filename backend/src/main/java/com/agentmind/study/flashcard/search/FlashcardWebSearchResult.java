package com.agentmind.study.flashcard.search;

/** 搜索适配器返回给应用层的统一结果，避免业务服务依赖某个供应商的响应结构。 */
public record FlashcardWebSearchResult(
        String title,
        String snippet,
        String url
) {
}
