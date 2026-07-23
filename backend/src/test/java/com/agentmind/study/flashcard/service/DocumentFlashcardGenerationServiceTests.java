package com.agentmind.study.flashcard.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentmind.document.chunk.DocumentChunk;
import com.agentmind.document.model.DocumentSourceType;
import com.agentmind.document.repository.InMemoryDocumentChunkRepository;
import com.agentmind.document.repository.InMemoryDocumentMetadataRepository;
import com.agentmind.study.flashcard.model.dto.GenerateFlashcardsFromDocumentsRequest;
import com.agentmind.study.flashcard.model.dto.StudyFlashcardResponse;
import com.agentmind.study.flashcard.repository.InMemoryStudyFlashcardRepository;
import com.agentmind.workspace.repository.InMemoryKnowledgeWorkspaceRepository;
import com.agentmind.workspace.service.WorkspaceAccessService;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 验证用户从指定知识资产直接制卡并保留来源关系。 */
class DocumentFlashcardGenerationServiceTests {

    @Test
    void shouldGenerateDueFlashcardsFromSelectedDocument() {
        InMemoryDocumentMetadataRepository documentRepository = new InMemoryDocumentMetadataRepository();
        InMemoryDocumentChunkRepository chunkRepository = new InMemoryDocumentChunkRepository();
        InMemoryStudyFlashcardRepository flashcardRepository = new InMemoryStudyFlashcardRepository();
        var metadata = documentRepository.create(
                1L, 1L, "Java 线程池", DocumentSourceType.MARKDOWN, null, "thread-pool.md", List.of("Java"));
        documentRepository.markSucceeded(metadata.id(), "storage-key", "text/markdown", 100, "hash", 2);
        chunkRepository.replaceDocumentChunks(1L, 1L, metadata.id(), List.of(
                new DocumentChunk(metadata.id() + "-0", metadata.id(), 0, "核心参数",
                        "核心线程数决定常驻工作线程数量。", 0, 17),
                new DocumentChunk(metadata.id() + "-1", metadata.id(), 1, "拒绝策略",
                        "任务饱和后由拒绝策略保护系统。", 18, 34)
        ));
        StudyFlashcardApplicationService flashcardService = new StudyFlashcardApplicationService(
                flashcardRepository, context -> { }, new StudyFlashcardResponseMapper());
        DocumentFlashcardGenerationService service = new DocumentFlashcardGenerationService(
                documentRepository,
                chunkRepository,
                flashcardService,
                new WorkspaceAccessService(new InMemoryKnowledgeWorkspaceRepository()),
                new LocalDocumentFlashcardCandidateGenerator()
        );

        List<StudyFlashcardResponse> created = service.generate(
                1L, 1L, new GenerateFlashcardsFromDocumentsRequest(List.of(metadata.id()), 2));

        assertThat(created).hasSize(2);
        assertThat(created).allMatch(card -> card.sourceDocumentId().equals(metadata.id()));
        assertThat(created).allMatch(card -> !card.dueAt().isAfter(java.time.OffsetDateTime.now()));
        assertThat(created).extracting(StudyFlashcardResponse::question)
                .anyMatch(question -> question.contains("核心线程数"));
        assertThat(created).allMatch(card -> card.answer().length() <= 260);
        assertThat(created).noneMatch(card -> card.question().contains("核心内容是什么"));
    }
}
