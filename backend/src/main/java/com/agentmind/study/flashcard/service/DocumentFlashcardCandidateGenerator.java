package com.agentmind.study.flashcard.service;

import java.util.List;

/** 将一组知识片段转换为具体、短小且可追溯的问答卡片。 */
public interface DocumentFlashcardCandidateGenerator {

    List<GeneratedDocumentFlashcard> generate(List<DocumentFlashcardSource> sources, int requestedCount);
}
