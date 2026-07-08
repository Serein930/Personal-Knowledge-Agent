package com.agentmind.document.model;

/**
 * 知识文档来源类型。
 *
 * <p>该枚举同时服务于文件上传、网页采集、文档列表筛选和 RAG 元数据过滤。
 * 前端展示时可以映射为 PDF、Markdown、网页文章等中文标签。</p>
 */
public enum DocumentSourceType {
    PDF,
    MARKDOWN,
    WEB_PAGE,
    WORD,
    TEXT,
    CODE
}
