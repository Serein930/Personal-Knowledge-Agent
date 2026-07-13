package com.agentmind.chat.memory.model;

/**
 * 会话消息角色。
 *
 * <p>当前短期记忆只保存用户和助手消息。系统提示词、检索片段和工具结果拥有独立的数据来源，
 * 不直接混入用户可查询的会话历史。</p>
 */
public enum ChatMessageRole {

    USER,

    ASSISTANT
}
