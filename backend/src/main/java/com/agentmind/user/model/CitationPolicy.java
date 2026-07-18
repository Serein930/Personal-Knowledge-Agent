package com.agentmind.user.model;

/** 用户希望回答如何展示知识来源。 */
public enum CitationPolicy {
    /** 只要回答基于知识库生成，就必须展示可用引用。 */
    REQUIRED,

    /** 有可靠检索来源时展示引用，资料不足时允许直接拒答。 */
    WHEN_AVAILABLE
}
