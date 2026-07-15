package com.agentmind.evaluation.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 为已有评估集创建下一不可变版本。 */
public record CreateRagEvaluationDatasetVersionRequest(
        @Size(max = 500, message = "版本说明不能超过500个字符") String changeNote,
        @NotEmpty(message = "评估集版本至少需要一道题") List<@Valid RagEvaluationCaseRequest> cases
) {
}
