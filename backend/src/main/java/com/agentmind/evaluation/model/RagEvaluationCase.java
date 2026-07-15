package com.agentmind.evaluation.model;

import java.util.List;

/**
 * 固定评估集中的单个问题。
 *
 * <p>片段编号优先用于精确检索评估；文档编号适合正文重新切分后仍保持较稳定的粗粒度评估。
 * 无答案问题必须设置 {@code expectedRefusal=true}，用于验证资料不足时是否正确拒答。</p>
 */
public record RagEvaluationCase(
        String caseKey,
        String question,
        List<String> expectedRelevantChunkIds,
        List<Long> expectedRelevantDocumentIds,
        boolean expectedRefusal,
        List<String> expectedAnswerKeywords
) {
}
