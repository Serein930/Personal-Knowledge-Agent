package com.agentmind.study.flashcard.controller;

import com.agentmind.common.response.ApiResponse;
import com.agentmind.common.security.CurrentUserId;
import com.agentmind.study.flashcard.model.dto.GenerateFlashcardsFromDocumentsRequest;
import com.agentmind.study.flashcard.model.dto.StudyFlashcardResponse;
import com.agentmind.study.flashcard.service.DocumentFlashcardGenerationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 指定知识资产直接制卡接口。 */
@Validated
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/flashcards")
public class DocumentFlashcardController {

    private final DocumentFlashcardGenerationService generationService;

    public DocumentFlashcardController(DocumentFlashcardGenerationService generationService) {
        this.generationService = generationService;
    }

    @PostMapping("/generate-from-documents")
    public ApiResponse<List<StudyFlashcardResponse>> generate(
            @PathVariable @Positive Long workspaceId,
            @CurrentUserId @Positive Long ownerUserId,
            @Valid @RequestBody GenerateFlashcardsFromDocumentsRequest request
    ) {
        return ApiResponse.success(generationService.generate(ownerUserId, workspaceId, request));
    }
}
