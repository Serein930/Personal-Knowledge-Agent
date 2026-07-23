package com.agentmind.study.flashcard.service;

/**
 * 提供给复习卡片生成器的最小知识来源。
 *
 * <p>生成器只能看到当前用户已选文档的片段，并且必须在结果中回传片段编号，
 * 业务服务会再次校验来源，防止模型把卡片错误关联到其他知识资产。</p>
 */
public record DocumentFlashcardSource(
        Long documentId,
        String documentTitle,
        String chunkId,
        String topic,
        String content
) {
}
