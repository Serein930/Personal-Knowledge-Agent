package com.agentmind.chat.model;

/**
 * 检索增强生成流式事件类型。
 *
 * <p>枚举中的事件名称是前后端共同依赖的协议字段，新增事件时应保持已有名称兼容。</p>
 */
public enum RagStreamEventType {

    METADATA("metadata"),
    DELTA("delta"),
    CITATION("citation"),
    TOOL_CALL("tool_call"),
    COMPLETE("complete"),
    ERROR("error");

    private final String eventName;

    RagStreamEventType(String eventName) {
        this.eventName = eventName;
    }

    public String eventName() {
        return eventName;
    }
}
