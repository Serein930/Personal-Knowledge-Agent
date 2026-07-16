package com.agentmind.evaluation.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 可在 Git 中版本化的评估集交换文档。 */
public record RagEvaluationDatasetExchangeDocument(
        @NotBlank(message = "评估集名称不能为空")
        @Size(max = 120, message = "评估集名称不能超过120个字符") String name,
        @Size(max = 1000, message = "评估集说明不能超过1000个字符") String description,
        @Size(max = 500, message = "版本说明不能超过500个字符") String changeNote,
        @NotEmpty(message = "评估集至少需要一道题") List<@Valid RagEvaluationCaseRequest> cases
) {
}
