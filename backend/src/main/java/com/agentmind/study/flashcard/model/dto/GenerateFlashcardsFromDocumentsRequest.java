package com.agentmind.study.flashcard.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 用户从指定知识资产直接生成复习卡片的请求。 */
public record GenerateFlashcardsFromDocumentsRequest(
        @NotEmpty(message = "至少选择一个文档")
        @Size(max = 10, message = "一次最多选择 10 个文档")
        List<Long> documentIds,

        @Min(value = 1, message = "至少生成 1 张卡片")
        @Max(value = 20, message = "一次最多生成 20 张卡片")
        int count
) {
}
