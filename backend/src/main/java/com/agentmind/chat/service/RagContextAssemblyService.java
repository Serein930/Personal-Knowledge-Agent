package com.agentmind.chat.service;

import com.agentmind.chat.model.dto.RagChatRequest;
import com.agentmind.chat.model.dto.RagChatResponse;
import com.agentmind.chat.model.dto.RagCitationResponse;
import com.agentmind.chat.model.dto.RagRetrievalContextResponse;
import com.agentmind.knowledge.model.dto.KnowledgeSearchResponse;
import com.agentmind.knowledge.model.dto.KnowledgeSearchResultResponse;
import com.agentmind.knowledge.service.KnowledgeSearchService;
import com.agentmind.chat.memory.service.ChatMemoryService;
import com.agentmind.chat.memory.service.ChatTurnContext;
import com.agentmind.user.model.dto.UserWorkspacePreferenceResponse;
import com.agentmind.user.service.UserWorkspacePreferenceService;
import com.agentmind.document.repository.DocumentMetadataRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 检索增强生成检索上下文与回答生成编排服务。
 *
 * <p>该服务负责串联向量检索、引用构造、提示词模板、拒答策略和回答生成端口。
 * 控制层不直接接触检索细节或模型客户端，后续接入真实模型时也可以保持接口稳定。</p>
 */
@Service
public class RagContextAssemblyService {

    private final KnowledgeSearchService knowledgeSearchService;
    private final AnswerGenerator answerGenerator;
    private final RagPromptTemplate promptTemplate;
    private final RagRefusalPolicy refusalPolicy;
    private final ChatMemoryService chatMemoryService;
    private final UserWorkspacePreferenceService preferenceService;
    private final DocumentMetadataRepository documentRepository;

    @Autowired
    public RagContextAssemblyService(
            KnowledgeSearchService knowledgeSearchService,
            AnswerGenerator answerGenerator,
            RagPromptTemplate promptTemplate,
            RagRefusalPolicy refusalPolicy,
            ChatMemoryService chatMemoryService,
            UserWorkspacePreferenceService preferenceService,
            DocumentMetadataRepository documentRepository
    ) {
        this.knowledgeSearchService = knowledgeSearchService;
        this.answerGenerator = answerGenerator;
        this.promptTemplate = promptTemplate;
        this.refusalPolicy = refusalPolicy;
        this.chatMemoryService = chatMemoryService;
        this.preferenceService = preferenceService;
        this.documentRepository = documentRepository;
    }

    /** 为不启动 Spring 容器的历史单元测试保留轻量构造方式。 */
    public RagContextAssemblyService(
            KnowledgeSearchService knowledgeSearchService,
            AnswerGenerator answerGenerator,
            RagPromptTemplate promptTemplate,
            RagRefusalPolicy refusalPolicy,
            ChatMemoryService chatMemoryService
    ) {
        this.knowledgeSearchService = knowledgeSearchService;
        this.answerGenerator = answerGenerator;
        this.promptTemplate = promptTemplate;
        this.refusalPolicy = refusalPolicy;
        this.chatMemoryService = chatMemoryService;
        this.preferenceService = null;
        this.documentRepository = null;
    }

    public RagChatResponse prepareChatContext(Long workspaceId, RagChatRequest request) {
        return prepareChatContext(1L, workspaceId, request);
    }

    /**
     * 使用已认证用户的身份准备并生成回答，避免审计、偏好和后续模型路由落到演示用户名下。
     */
    public RagChatResponse prepareChatContext(Long ownerUserId, Long workspaceId, RagChatRequest request) {
        PreparedRagChat preparedChat = prepareChat(ownerUserId, workspaceId, request);
        GeneratedAnswer generatedAnswer;
        try {
            generatedAnswer = answerGenerator.generate(preparedChat.generationRequest());
            chatMemoryService.completeAssistant(
                    workspaceId,
                    preparedChat.conversationId(),
                    preparedChat.messageId(),
                    generatedAnswer.content()
            );
        } catch (RuntimeException exception) {
            chatMemoryService.failAssistant(
                    workspaceId,
                    preparedChat.conversationId(),
                    preparedChat.messageId(),
                    exception.getMessage()
            );
            throw exception;
        }

        return new RagChatResponse(
                preparedChat.conversationId(),
                preparedChat.messageId(),
                generatedAnswer.content(),
                preparedChat.retrievalContext(),
                preparedChat.citations(),
                generatedAnswer.toolCalls(),
                generatedAnswer.metadata(),
                generatedAnswer.usage()
        );
    }

    /**
     * 准备同步和流式问答共用的检索上下文与回答生成请求。
     *
     * <p>该方法只负责准备数据，不触发任何回答生成器，因此流式服务可以先发送元数据和引用事件，
     * 再把相同的生成请求交给流式生成端口。</p>
     */
    public PreparedRagChat prepareChat(Long workspaceId, RagChatRequest request) {
        return prepareChat(1L, workspaceId, request);
    }

    /** 同步和流式接口共用的、包含真实用户身份的上下文准备入口。 */
    public PreparedRagChat prepareChat(Long ownerUserId, Long workspaceId, RagChatRequest request) {
        ChatTurnContext turnContext = chatMemoryService.beginTurn(
                workspaceId,
                request.conversationId(),
                request.question()
        );
        try {
            return prepareChat(ownerUserId, workspaceId, request, turnContext);
        } catch (RuntimeException exception) {
            chatMemoryService.failAssistant(
                    workspaceId,
                    turnContext.conversation().id(),
                    turnContext.assistantMessage().id(),
                    exception.getMessage()
            );
            throw exception;
        }
    }

    private PreparedRagChat prepareChat(
            Long ownerUserId,
            Long workspaceId,
            RagChatRequest request,
            ChatTurnContext turnContext
    ) {
        UserWorkspacePreferenceResponse preference = preferenceService == null
                ? null : preferenceService.get(ownerUserId, workspaceId);
        int effectiveTopK = request.topK() != null
                ? request.topK() : preference == null ? 5 : preference.defaultTopK();
        KnowledgeSearchResponse searchResponse = knowledgeSearchService.search(
                workspaceId,
                request.question(),
                effectiveTopK,
                preference == null ? null : preference.embeddingModel(),
                request.filters() == null || request.filters().documentIds() == null
                        ? java.util.Set.of() : java.util.Set.copyOf(request.filters().documentIds())
        );
        List<RagCitationResponse> citations = toCitations(searchResponse.results());
        RagRefusalDecision refusalDecision = refusalPolicy.decide(citations);
        String promptContext = promptTemplate.buildPromptContext(
                request.question(),
                citations,
                turnContext.history()
        );
        String generationPrompt = promptTemplate.buildGenerationPrompt(
                request.question(), promptContext, refusalDecision,
                preference == null ? com.agentmind.user.model.CitationPolicy.REQUIRED : preference.citationPolicy()
        );
        RagRetrievalContextResponse retrievalContext = new RagRetrievalContextResponse(
                request.question(),
                searchResponse.topK(),
                promptTemplate.promptVersion(),
                promptContext,
                citations
        );
        AnswerGenerationRequest generationRequest = new AnswerGenerationRequest(
                workspaceId,
                ownerUserId,
                turnContext.conversation().id(),
                turnContext.assistantMessage().id(),
                request.question(),
                promptTemplate.promptVersion(),
                retrievalContext.promptContext(),
                generationPrompt,
                preference == null ? null : preference.chatModel(),
                citations,
                refusalDecision
        );

        return new PreparedRagChat(
                turnContext.conversation().id(),
                turnContext.assistantMessage().id(),
                retrievalContext,
                citations,
                generationRequest
        );
    }

    private List<RagCitationResponse> toCitations(List<KnowledgeSearchResultResponse> results) {
        return java.util.stream.IntStream.range(0, results.size())
                .mapToObj(index -> toCitation(index + 1, results.get(index)))
                .toList();
    }

    private RagCitationResponse toCitation(int index, KnowledgeSearchResultResponse result) {
        String documentTitle = documentRepository == null ? "Document #" + result.documentId()
                : documentRepository.findById(result.documentId())
                .map(com.agentmind.document.model.DocumentMetadata::title)
                .orElse("文档 #" + result.documentId());
        return new RagCitationResponse(
                index,
                result.documentId(),
                documentTitle,
                result.chunkId(),
                result.chunkSequence(),
                result.headingPath(),
                result.content(),
                result.score()
        );
    }

}
