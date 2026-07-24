package com.agentmind.study.flashcard.search;

import java.util.List;

/**
 * 复习卡片外部搜索端口。
 *
 * <p>实现类只负责供应商协议、鉴权与响应解析；结果裁剪和用户可见降级文案由应用服务统一处理。</p>
 */
public interface FlashcardWebSearchClient {

    FlashcardWebSearchProvider provider();

    boolean isConfigured();

    List<FlashcardWebSearchResult> search(String query, int resultCount);
}
