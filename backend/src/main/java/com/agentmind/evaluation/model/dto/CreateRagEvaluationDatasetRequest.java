package com.agentmind.evaluation.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 创建固定评估集及首个不可变版本。 */
public record CreateRagEvaluationDatasetRequest(
        @NotBlank(message = "评估集名称不能为空")
        @Size(max = 120, message = "评估集名称不能超过120个字符") String name,
        @Size(max = 1000, message = "评估集说明不能超过1000个字符") String description,
        @NotEmpty(message = "评估集至少需要一道题") List<@Valid RagEvaluationCaseRequest> cases
) {
}
