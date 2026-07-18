package com.agentmind.dashboard.model.dto;

import com.agentmind.document.model.dto.DocumentSummaryResponse;
import com.agentmind.study.plan.model.dto.DailyStudyTaskResponse;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 工作台聚合响应。
 *
 * <p>工作台只返回首屏展示所需的汇总数据，避免前端为同一个页面并发调用多个业务接口。
 * 最近文档和学习任务继续复用各业务模块的公开响应结构，保证字段含义与详情页面一致。</p>
 */
public record DashboardOverviewResponse(
        long knowledgeAssetCount,
        long ingestedToday,
        long pendingIngestionCount,
        int todayPlanTaskCount,
        long dueFlashcardCount,
        long agentCallCount,
        long averageAgentLatencyMillis,
        List<DocumentSummaryResponse> recentDocuments,
        List<DailyStudyTaskResponse> studyTasks,
        OffsetDateTime generatedAt
) {

    public DashboardOverviewResponse {
        recentDocuments = List.copyOf(recentDocuments);
        studyTasks = List.copyOf(studyTasks);
    }
}
