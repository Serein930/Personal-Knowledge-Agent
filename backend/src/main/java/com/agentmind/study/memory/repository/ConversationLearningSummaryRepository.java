package com.agentmind.study.memory.repository;

import com.agentmind.study.memory.model.ConversationLearningSummary;
import java.util.List;

/** 长期会话摘要存储端口。 */
public interface ConversationLearningSummaryRepository {

    ConversationLearningSummary saveOrUpdate(ConversationLearningSummary summary);

    List<ConversationLearningSummary> findByScope(Long ownerUserId, Long workspaceId, int limit);
}
