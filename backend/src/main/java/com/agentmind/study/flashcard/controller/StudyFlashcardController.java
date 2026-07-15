package com.agentmind.study.flashcard.controller;

import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.common.response.ApiResponse;
import com.agentmind.common.response.PageResponse;
import com.agentmind.study.flashcard.model.dto.StudyFlashcardResponse;
import com.agentmind.study.flashcard.model.dto.StudyFlashcardReviewResponse;
import com.agentmind.study.flashcard.model.dto.ManageFlashcardRequest;
import com.agentmind.study.flashcard.model.dto.RescheduleFlashcardRequest;
import com.agentmind.study.flashcard.model.dto.StudyReviewStatisticsResponse;
import com.agentmind.study.flashcard.model.dto.SubmitFlashcardReviewRequest;
import com.agentmind.study.flashcard.model.dto.SubmittedFlashcardReviewResponse;
import com.agentmind.study.flashcard.service.StudyFlashcardApplicationService;
import com.agentmind.study.flashcard.service.StudyFlashcardReviewApplicationService;
import com.agentmind.study.flashcard.service.StudyReviewStatisticsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 复习卡片查询、管理、评分与学习统计接口。
 *
 * <p>卡片内容创建仍只允许通过已确认的智能体工具完成；暂停、恢复、排期和评分属于用户明确触发的
 * 学习行为，可以通过本控制器执行，但每次仍必须校验用户和知识空间归属。</p>
 */
@Validated
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/flashcards")
public class StudyFlashcardController {

    private final StudyFlashcardApplicationService flashcardApplicationService;
    private final StudyFlashcardReviewApplicationService reviewApplicationService;
    private final StudyReviewStatisticsService statisticsService;

    public StudyFlashcardController(
            StudyFlashcardApplicationService flashcardApplicationService,
            StudyFlashcardReviewApplicationService reviewApplicationService,
            StudyReviewStatisticsService statisticsService
    ) {
        this.flashcardApplicationService = flashcardApplicationService;
        this.reviewApplicationService = reviewApplicationService;
        this.statisticsService = statisticsService;
    }

    @GetMapping
    public ApiResponse<PageResponse<StudyFlashcardResponse>> list(
            @PathVariable @Positive(message = "知识空间编号必须为正数") Long workspaceId,
            @RequestHeader(name = "X-Demo-User-Id", defaultValue = "1")
            @Positive(message = "演示用户编号必须为正数") Long ownerUserId,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码必须大于0") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "每页数量必须大于0")
            @Max(value = 100, message = "每页数量不能大于100") int pageSize
    ) {
        return ApiResponse.success(flashcardApplicationService.list(
                new AgentToolExecutionContext(ownerUserId, workspaceId, null), page, pageSize
        ));
    }

    /**
     * 查询当前时间已经到期的新卡片或复习卡片。
     */
    @GetMapping("/due")
    public ApiResponse<PageResponse<StudyFlashcardResponse>> listDue(
            @PathVariable @Positive(message = "知识空间编号必须为正数") Long workspaceId,
            @RequestHeader(name = "X-Demo-User-Id", defaultValue = "1")
            @Positive(message = "演示用户编号必须为正数") Long ownerUserId,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码必须大于0") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "每页数量必须大于0")
            @Max(value = 100, message = "每页数量不能大于100") int pageSize
    ) {
        return ApiResponse.success(flashcardApplicationService.listDue(
                new AgentToolExecutionContext(ownerUserId, workspaceId, null), page, pageSize
        ));
    }

    /**
     * 提交一次 0 到 5 分的回忆质量评分，并原子推进卡片调度状态。
     */
    @PostMapping("/{flashcardId}/reviews")
    public ApiResponse<SubmittedFlashcardReviewResponse> submitReview(
            @PathVariable @Positive(message = "知识空间编号必须为正数") Long workspaceId,
            @PathVariable @Positive(message = "复习卡片编号必须为正数") Long flashcardId,
            @RequestHeader(name = "X-Demo-User-Id", defaultValue = "1")
            @Positive(message = "演示用户编号必须为正数") Long ownerUserId,
            @Valid @RequestBody SubmitFlashcardReviewRequest request
    ) {
        return ApiResponse.success(reviewApplicationService.submit(
                new AgentToolExecutionContext(ownerUserId, workspaceId, null), flashcardId, request
        ));
    }

    /**
     * 分页查询指定卡片的评分历史。
     */
    @GetMapping("/{flashcardId}/reviews")
    public ApiResponse<PageResponse<StudyFlashcardReviewResponse>> listReviews(
            @PathVariable @Positive(message = "知识空间编号必须为正数") Long workspaceId,
            @PathVariable @Positive(message = "复习卡片编号必须为正数") Long flashcardId,
            @RequestHeader(name = "X-Demo-User-Id", defaultValue = "1")
            @Positive(message = "演示用户编号必须为正数") Long ownerUserId,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码必须大于0") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "每页数量必须大于0")
            @Max(value = 100, message = "每页数量不能大于100") int pageSize
    ) {
        return ApiResponse.success(reviewApplicationService.listReviews(
                new AgentToolExecutionContext(ownerUserId, workspaceId, null), flashcardId, page, pageSize
        ));
    }

    /**
     * 暂停卡片，使其暂时离开到期队列。
     */
    @PostMapping("/{flashcardId}/suspend")
    public ApiResponse<StudyFlashcardResponse> suspend(
            @PathVariable @Positive(message = "知识空间编号必须为正数") Long workspaceId,
            @PathVariable @Positive(message = "复习卡片编号必须为正数") Long flashcardId,
            @RequestHeader(name = "X-Demo-User-Id", defaultValue = "1")
            @Positive(message = "演示用户编号必须为正数") Long ownerUserId,
            @Valid @RequestBody ManageFlashcardRequest request
    ) {
        return ApiResponse.success(flashcardApplicationService.suspend(
                new AgentToolExecutionContext(ownerUserId, workspaceId, null), flashcardId, request
        ));
    }

    /**
     * 恢复暂停卡片。已经过期的卡片会立即重新进入到期队列。
     */
    @PostMapping("/{flashcardId}/resume")
    public ApiResponse<StudyFlashcardResponse> resume(
            @PathVariable @Positive(message = "知识空间编号必须为正数") Long workspaceId,
            @PathVariable @Positive(message = "复习卡片编号必须为正数") Long flashcardId,
            @RequestHeader(name = "X-Demo-User-Id", defaultValue = "1")
            @Positive(message = "演示用户编号必须为正数") Long ownerUserId,
            @Valid @RequestBody ManageFlashcardRequest request
    ) {
        return ApiResponse.success(flashcardApplicationService.resume(
                new AgentToolExecutionContext(ownerUserId, workspaceId, null), flashcardId, request
        ));
    }

    /**
     * 将活动卡片调整到指定的未来时间。
     */
    @PostMapping("/{flashcardId}/reschedule")
    public ApiResponse<StudyFlashcardResponse> reschedule(
            @PathVariable @Positive(message = "知识空间编号必须为正数") Long workspaceId,
            @PathVariable @Positive(message = "复习卡片编号必须为正数") Long flashcardId,
            @RequestHeader(name = "X-Demo-User-Id", defaultValue = "1")
            @Positive(message = "演示用户编号必须为正数") Long ownerUserId,
            @Valid @RequestBody RescheduleFlashcardRequest request
    ) {
        return ApiResponse.success(flashcardApplicationService.reschedule(
                new AgentToolExecutionContext(ownerUserId, workspaceId, null), flashcardId, request
        ));
    }

    /**
     * 返回复习工作台需要的统一统计口径。
     */
    @GetMapping("/statistics")
    public ApiResponse<StudyReviewStatisticsResponse> statistics(
            @PathVariable @Positive(message = "知识空间编号必须为正数") Long workspaceId,
            @RequestHeader(name = "X-Demo-User-Id", defaultValue = "1")
            @Positive(message = "演示用户编号必须为正数") Long ownerUserId
    ) {
        return ApiResponse.success(statisticsService.summarize(
                new AgentToolExecutionContext(ownerUserId, workspaceId, null)
        ));
    }
}
