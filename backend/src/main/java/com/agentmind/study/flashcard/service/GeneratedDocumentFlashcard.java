package com.agentmind.study.flashcard.service;

/** 经过生成器初步整理、尚未写入仓储的原子问答卡片。 */
public record GeneratedDocumentFlashcard(
        Long sourceDocumentId,
        String sourceChunkId,
        String topic,
        String question,
        String answer,
        String explanation
) {
}
