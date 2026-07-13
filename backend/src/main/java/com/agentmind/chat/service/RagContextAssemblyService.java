package com.agentmind.chat.service;

import com.agentmind.chat.model.dto.RagChatRequest;
import com.agentmind.chat.model.dto.RagChatResponse;
import com.agentmind.chat.model.dto.RagCitationResponse;
import com.agentmind.chat.model.dto.RagRetrievalContextResponse;
import com.agentmind.knowledge.model.dto.KnowledgeSearchResponse;
import com.agentmind.knowledge.model.dto.KnowledgeSearchResultResponse;
import com.agentmind.knowledge.service.KnowledgeSearchService;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
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
    private final AtomicLong messageIdGenerator = new AtomicLong(10_000);

    public RagContextAssemblyService(
            KnowledgeSearchService knowledgeSearchService,
            AnswerGenerator answerGenerator,
            RagPromptTemplate promptTemplate,
            RagRefusalPolicy refusalPolicy
    ) {
        this.knowledgeSearchService = knowledgeSearchService;
        this.answerGenerator = answerGenerator;
        this.promptTemplate = promptTemplate;
        this.refusalPolicy = refusalPolicy;
    }

    public RagChatResponse prepareChatContext(Long workspaceId, RagChatRequest request) {
        PreparedRagChat preparedChat = prepareChat(workspaceId, request);
        GeneratedAnswer generatedAnswer = answerGenerator.generate(preparedChat.generationRequest());

        return new RagChatResponse(
                preparedChat.conversationId(),
                preparedChat.messageId(),
                generatedAnswer.content(),
                preparedChat.retrievalContext(),
                preparedChat.citations(),
                List.of(),
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
        KnowledgeSearchResponse searchResponse = knowledgeSearchService.search(
                workspaceId,
                request.question(),
                request.topK()
        );
        List<RagCitationResponse> citations = toCitations(searchResponse.results());
        RagRefusalDecision refusalDecision = refusalPolicy.decide(citations);
        String promptContext = promptTemplate.buildPromptContext(request.question(), citations);
        String generationPrompt = promptTemplate.buildGenerationPrompt(request.question(), promptContext, refusalDecision);
        RagRetrievalContextResponse retrievalContext = new RagRetrievalContextResponse(
                request.question(),
                searchResponse.topK(),
                promptTemplate.promptVersion(),
                promptContext,
                citations
        );
        AnswerGenerationRequest generationRequest = new AnswerGenerationRequest(
                workspaceId,
                request.question(),
                promptTemplate.promptVersion(),
                retrievalContext.promptContext(),
                generationPrompt,
                citations,
                refusalDecision
        );

        return new PreparedRagChat(
                request.conversationId(),
                messageIdGenerator.incrementAndGet(),
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
        return new RagCitationResponse(
                index,
                result.documentId(),
                "Document #" + result.documentId(),
                result.chunkId(),
                result.chunkSequence(),
                result.headingPath(),
                result.content(),
                result.score()
        );
    }

}
