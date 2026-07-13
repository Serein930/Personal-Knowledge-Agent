package com.agentmind.chat.model.dto;

import com.agentmind.chat.model.RagModelCallStatus;
import java.time.OffsetDateTime;

/**
 * 模型调用观测记录查询响应。
 *
 * <p>响应只暴露评估与故障排查需要的摘要字段，不返回完整提示词、用户问题或检索正文，
 * 避免审计接口泄露个人知识内容。</p>
 */
public record RagModelCallObservationResponse(
        String id,
        String promptVersion,
        String answerGenerator,
        String modelName,
        int citationCount,
        boolean refused,
        RagModelCallStatus status,
        long elapsedMillis,
        int answerLength,
        String failureReason,
        OffsetDateTime createdAt
) {
}
