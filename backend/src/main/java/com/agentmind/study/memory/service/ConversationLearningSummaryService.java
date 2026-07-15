package com.agentmind.study.memory.service;

import com.agentmind.agent.service.AgentToolExecutionAuthorizer;
import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.chat.memory.model.ChatConversation;
import com.agentmind.chat.memory.model.ChatMessage;
import com.agentmind.chat.memory.model.ChatMessageRole;
import com.agentmind.chat.memory.model.ChatMessageStatus;
import com.agentmind.chat.memory.repository.ChatMemoryRepository;
import com.agentmind.study.memory.model.ConversationLearningSummary;
import com.agentmind.study.memory.model.dto.ConversationLearningSummaryResponse;
import com.agentmind.study.memory.repository.ConversationLearningSummaryRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 把短期会话压缩为可长期使用的学习信号。
 *
 * <p>当前阶段使用确定性摘要，避免测试依赖真实模型。只读取成功完成的消息；失败或取消回答不会
 * 进入长期记忆。主题来自知识空间现有学习画像，弱项信号来自用户问题中的明确表达。</p>
 */
@Service
public class ConversationLearningSummaryService {

    private static final int CONVERSATION_LIMIT = 20;
    private static final int MESSAGE_LIMIT = 100;
    private static final int SUMMARY_QUESTION_LIMIT = 3;
    private static final List<String> WEAK_MARKERS = List.of("不会", "不懂", "忘记", "薄弱", "不清楚", "很难");

    private final ConversationLearningSummaryRepository repository;
    private final ChatMemoryRepository chatMemoryRepository;
    private final AgentToolExecutionAuthorizer authorizer;

    public ConversationLearningSummaryService(
            ConversationLearningSummaryRepository repository,
            ChatMemoryRepository chatMemoryRepository,
            AgentToolExecutionAuthorizer authorizer
    ) {
        this.repository = repository;
        this.chatMemoryRepository = chatMemoryRepository;
        this.authorizer = authorizer;
    }

    public List<ConversationLearningSummaryResponse> get(AgentToolExecutionContext context) {
        authorizer.authorize(context);
        return repository.findByScope(context.ownerUserId(), context.workspaceId(), CONVERSATION_LIMIT)
                .stream().map(this::toResponse).toList();
    }

    public List<ConversationLearningSummaryResponse> refresh(
            AgentToolExecutionContext context,
            List<String> knownTopics
    ) {
        authorizer.authorize(context);
        return refreshInternal(context, knownTopics).stream().map(this::toResponse).toList();
    }

    public List<ConversationLearningSummary> refreshInternal(
            AgentToolExecutionContext context,
            List<String> knownTopics
    ) {
        List<ChatConversation> conversations = chatMemoryRepository.findConversationsByWorkspaceId(
                context.workspaceId(), 0, CONVERSATION_LIMIT
        );
        for (ChatConversation conversation : conversations) {
            List<ChatMessage> messages = chatMemoryRepository.findMessagesByWorkspaceIdAndConversationId(
                            context.workspaceId(), conversation.id(), 0, MESSAGE_LIMIT
                    ).stream()
                    .filter(message -> message.status() == ChatMessageStatus.COMPLETED)
                    .toList();
            if (!messages.isEmpty()) {
                repository.saveOrUpdate(summarize(context, conversation, messages, knownTopics));
            }
        }
        return repository.findByScope(context.ownerUserId(), context.workspaceId(), CONVERSATION_LIMIT);
    }

    private ConversationLearningSummary summarize(
            AgentToolExecutionContext context,
            ChatConversation conversation,
            List<ChatMessage> messages,
            List<String> knownTopics
    ) {
        Set<String> topics = new LinkedHashSet<>();
        Set<String> weakTopics = new LinkedHashSet<>();
        List<String> userQuestions = new ArrayList<>();
        for (ChatMessage message : messages) {
            String content = message.content() == null ? "" : message.content().trim();
            if (message.role() == ChatMessageRole.USER && !content.isEmpty()) {
                userQuestions.add(content);
            }
            for (String topic : knownTopics) {
                if (!topic.isBlank() && content.toLowerCase(Locale.ROOT).contains(topic.toLowerCase(Locale.ROOT))) {
                    topics.add(topic);
                    if (message.role() == ChatMessageRole.USER && containsWeakMarker(content)) {
                        weakTopics.add(topic);
                    }
                }
            }
        }
        List<String> recentQuestions = userQuestions.stream()
                .skip(Math.max(0, userQuestions.size() - SUMMARY_QUESTION_LIMIT))
                .map(this::abbreviate)
                .toList();
        String summary = recentQuestions.isEmpty()
                ? abbreviate(conversation.title())
                : "近期关注：" + String.join("；", recentQuestions);
        OffsetDateTime now = OffsetDateTime.now();
        return new ConversationLearningSummary(
                null, context.ownerUserId(), context.workspaceId(), conversation.id(), summary,
                List.copyOf(topics), List.copyOf(weakTopics), messages.size(), 0, now, now
        );
    }

    private boolean containsWeakMarker(String content) {
        return WEAK_MARKERS.stream().anyMatch(content::contains);
    }

    private String abbreviate(String value) {
        if (value == null || value.isBlank()) {
            return "未命名学习会话";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 120 ? normalized : normalized.substring(0, 120);
    }

    private ConversationLearningSummaryResponse toResponse(ConversationLearningSummary summary) {
        return new ConversationLearningSummaryResponse(
                summary.id(), summary.conversationId(), summary.summary(), summary.topics(),
                summary.weakTopics(), summary.messageCount(), summary.version(), summary.updatedAt()
        );
    }
}
