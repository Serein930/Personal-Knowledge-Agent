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
 * 后续可追溯到原始知识资产。生成策略使用摄取阶段的语义标题和正文片段，保证离线可重复。</p>
 */
@Service
public class DocumentFlashcardGenerationService {

    private final DocumentMetadataRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final StudyFlashcardApplicationService flashcardService;
    private final WorkspaceAccessService workspaceAccessService;

    public DocumentFlashcardGenerationService(
            DocumentMetadataRepository documentRepository,
            DocumentChunkRepository chunkRepository,
            StudyFlashcardApplicationService flashcardService,
            WorkspaceAccessService workspaceAccessService
    ) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.flashcardService = flashcardService;
        this.workspaceAccessService = workspaceAccessService;
    }

    public List<StudyFlashcardResponse> generate(
            Long ownerUserId,
            Long workspaceId,
            GenerateFlashcardsFromDocumentsRequest request
    ) {
        workspaceAccessService.requireWritable(ownerUserId, workspaceId);
        List<SourceChunk> candidates = new ArrayList<>();
        for (Long documentId : request.documentIds().stream().distinct().toList()) {
            DocumentMetadata document = documentRepository.findByWorkspaceIdAndId(workspaceId, documentId)
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.RESOURCE_NOT_FOUND, "选择的文档不存在或无权访问"));
            if (document.ingestionStatus() != IngestionStatus.SUCCEEDED) {
                throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "文档尚未完成摄取：" + document.title());
            }
            chunkRepository.findAllByDocumentId(documentId).stream()
                    .filter(chunk -> StringUtils.hasText(chunk.content()))
                    .forEach(chunk -> candidates.add(new SourceChunk(document, chunk)));
        }
        if (candidates.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "所选文档没有可用于制卡的文本片段");
        }

        int targetCount = Math.min(request.count(), candidates.size());
        List<StudyFlashcardResponse> created = new ArrayList<>();
        for (int index = 0; index < targetCount; index++) {
            int candidateIndex = targetCount == 1 ? 0 : index * (candidates.size() - 1) / (targetCount - 1);
            SourceChunk source = candidates.get(candidateIndex);
            String topic = StringUtils.hasText(source.chunk().headingPath())
                    ? source.chunk().headingPath().trim() : source.document().title();
            String question = "《" + source.document().title() + "》中“" + limit(topic, 120) + "”的核心内容是什么？";
            String answer = limit(source.chunk().content().replaceAll("\\s+", " ").trim(), 1800);
            AgentToolExecutionContext context = new AgentToolExecutionContext(ownerUserId, workspaceId, null)
                    .withRequestId("direct-card-" + UUID.randomUUID());
            created.add(flashcardService.createFromAgent(
                    context,
                    question,
                    answer,
                    "由用户从指定知识资产直接生成，来源片段：" + source.chunk().id(),
                    source.document().id(),
                    limit(topic, 100)
            ));
        }
        return List.copyOf(created);
    }

    private String limit(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private record SourceChunk(DocumentMetadata document, DocumentChunk chunk) {
    }
}
