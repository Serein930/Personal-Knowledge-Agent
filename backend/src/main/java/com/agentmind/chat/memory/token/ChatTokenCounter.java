package com.agentmind.chat.memory.token;

import com.agentmind.chat.memory.model.ChatMessageRole;

/**
 * 短期会话记忆的令牌计算端口。
 *
 * <p>应用服务只依赖该端口，不直接绑定某个模型或分词器。后续接入不同模型时，可以按模型名称
 * 选择对应编码实现，而无需修改滑动窗口业务规则。</p>
 */
@FunctionalInterface
public interface ChatTokenCounter {

    int countTokens(ChatMessageRole role, String content);
}
