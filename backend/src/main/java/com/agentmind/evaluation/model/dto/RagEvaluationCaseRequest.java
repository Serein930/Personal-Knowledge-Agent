package com.agentmind.evaluation.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 固定评估题目请求。跨字段业务约束由应用服务统一校验。 */
public record RagEvaluationCaseRequest(
        @NotBlank(message = "评估题目标识不能为空")
        @Size(max = 100, message = "评估题目标识不能超过100个字符") String caseKey,
        @NotBlank(message = "评估问题不能为空")
        @Size(max = 2000, message = "评估问题不能超过2000个字符") String question,
        @Size(max = 100, message = "期望片段数量不能超过100") List<String> expectedRelevantChunkIds,
        @Size(max = 100, message = "期望文档数量不能超过100") List<Long> expectedRelevantDocumentIds,
        boolean expectedRefusal,
        @Size(max = 50, message = "答案关键词数量不能超过50") List<String> expectedAnswerKeywords
) {
}
