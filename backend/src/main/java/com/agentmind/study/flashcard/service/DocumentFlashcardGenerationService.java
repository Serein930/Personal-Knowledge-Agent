package com.agentmind.study.flashcard.service;

import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.common.exception.BusinessException;
import com.agentmind.common.exception.ErrorCode;
import com.agentmind.document.chunk.DocumentChunk;
import com.agentmind.document.model.DocumentMetadata;
import com.agentmind.document.model.IngestionStatus;
import com.agentmind.document.repository.DocumentChunkRepository;
import com.agentmind.document.repository.DocumentMetadataRepository;
import com.agentmind.study.flashcard.model.dto.GenerateFlashcardsFromDocumentsRequest;
import com.agentmind.study.flashcard.model.dto.StudyFlashcardResponse;
import com.agentmind.workspace.service.WorkspaceAccessService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 根据用户明确选择的文件或网页片段直接创建复习卡片。
 *
 * <p>该入口是显式用户操作，因此不需要智能体确认单。每张卡片仍保留来源文档编号，
 * 后续可追溯到原始知识资产。具体问答由独立生成端口负责，应用服务只编排权限、来源和写入流程。</p>
 */
@Service
public class DocumentFlashcardGenerationService {

    private final DocumentMetadataRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final StudyFlashcardApplicationService flashcardService;
    private final WorkspaceAccessService workspaceAccessService;
    private final DocumentFlashcardCandidateGenerator candidateGenerator;

    public DocumentFlashcardGenerationService(
            DocumentMetadataRepository documentRepository,
            DocumentChunkRepository chunkRepository,
            StudyFlashcardApplicationService flashcardService,
            WorkspaceAccessService workspaceAccessService,
            DocumentFlashcardCandidateGenerator candidateGenerator
    ) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.flashcardService = flashcardService;
        this.workspaceAccessService = workspaceAccessService;
        this.candidateGenerator = candidateGenerator;
    }

    public List<StudyFlashcardResponse> generate(
            Long ownerUserId,
            Long workspaceId,
            GenerateFlashcardsFromDocumentsRequest request
    ) {
        workspaceAccessService.requireWritable(ownerUserId, workspaceId);
        List<DocumentFlashcardSource> sources = new ArrayList<>();
        for (Long documentId : request.documentIds().stream().distinct().toList()) {
            DocumentMetadata document = documentRepository.findByWorkspaceIdAndId(workspaceId, documentId)
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.RESOURCE_NOT_FOUND, "选择的文档不存在或无权访问"));
            if (document.ingestionStatus() != IngestionStatus.SUCCEEDED) {
                throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "文档尚未完成摄取：" + document.title());
            }
            chunkRepository.findAllByDocumentId(documentId).stream()
                    .filter(chunk -> StringUtils.hasText(chunk.content()))
                    .forEach(chunk -> sources.add(toSource(document, chunk)));
        }
        if (sources.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "所选文档没有可用于制卡的文本片段");
        }

        List<GeneratedDocumentFlashcard> candidates = candidateGenerator.generate(sources, request.count());
        if (candidates.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.RESOURCE_CONFLICT,
                    "所选资料缺少能够形成具体问答的内容，请选择包含定义、原理或解释的资料"
            );
        }
        List<StudyFlashcardResponse> created = new ArrayList<>();
        for (GeneratedDocumentFlashcard candidate : candidates.stream().limit(request.count()).toList()) {
            AgentToolExecutionContext context = new AgentToolExecutionContext(ownerUserId, workspaceId, null)
                    .withRequestId("direct-card-" + UUID.randomUUID());
            created.add(flashcardService.createFromAgent(
                    context,
                    candidate.question(),
                    candidate.answer(),
                    candidate.explanation() + " 来源片段：" + candidate.sourceChunkId(),
                    candidate.sourceDocumentId(),
                    candidate.topic()
            ));
        }
        return List.copyOf(created);
    }

    private DocumentFlashcardSource toSource(DocumentMetadata document, DocumentChunk chunk) {
        String topic = StringUtils.hasText(chunk.headingPath())
                ? chunk.headingPath().trim() : document.title();
        return new DocumentFlashcardSource(
                document.id(),
                document.title(),
                chunk.id(),
                topic,
                chunk.content().trim()
        );
    }
}
