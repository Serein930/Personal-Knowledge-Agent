package com.agentmind.study.flashcard.fsrs.model.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** 人工调整用户级 FSRS 参数请求。 */
public record UpdateFsrsUserProfileRequest(
        @NotEmpty(message = "FSRS 参数不能为空")
        List<Double> parameters,

        @DecimalMin(value = "0.70", message = "期望保持率不能小于0.70")
        @DecimalMax(value = "0.99", message = "期望保持率不能大于0.99")
        double desiredRetention
) {
}
