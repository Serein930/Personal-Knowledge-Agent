package com.agentmind.study.plan.service;

import com.agentmind.study.flashcard.model.StudyFlashcard;
import com.agentmind.study.flashcard.model.StudyFlashcardStatus;
import com.agentmind.study.plan.model.DailyStudyTask;
import com.agentmind.study.plan.model.DailyStudyTaskPriority;
import com.agentmind.study.plan.model.DailyStudyTaskStatus;
import com.agentmind.study.plan.model.DailyStudyTaskType;
import com.agentmind.study.plan.model.dto.SaveDailyStudyPlanRequest;
import com.agentmind.study.memory.model.ConversationLearningSummary;
import com.agentmind.study.profile.model.LearningTopicLevel;
import com.agentmind.study.profile.model.LearningTopicProfile;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 根据卡片状态、知识来源和用户偏好生成每日学习任务。
 *
 * <p>该生成器是确定性的领域策略，不调用模型，因此同一输入始终得到相同任务。Agent 只负责提出
 * 偏好候选，最终任务仍由服务端规则生成，避免模型直接决定数据库关联和任务优先级。</p>
 */
@Component
public class DailyStudyTaskGenerator {

    private static final int MAX_WEAK_TOPICS = 3;

    public List<DailyStudyTask> generate(
            Long ownerUserId,
            Long workspaceId,
            List<StudyFlashcard> cards,
            List<LearningTopicProfile> learningProfiles,
            List<ConversationLearningSummary> conversationSummaries,
            SaveDailyStudyPlanRequest request,
            OffsetDateTime planDeadline,
            OffsetDateTime createdAt
    ) {
        List<DailyStudyTask> tasks = new ArrayList<>();
        List<StudyFlashcard> activeCards = cards.stream()
                .filter(card -> card.status() != StudyFlashcardStatus.SUSPENDED)
                .toList();
        addDueTask(tasks, ownerUserId, workspaceId, activeCards, request, planDeadline, createdAt);
        addWeakPointTasks(tasks, ownerUserId, workspaceId, activeCards, createdAt);
        addPreferredTopicTasks(tasks, ownerUserId, workspaceId, activeCards, request, createdAt);
        addDocumentTasks(tasks, ownerUserId, workspaceId, activeCards, request, createdAt);
        addProfileTasks(tasks, ownerUserId, workspaceId, activeCards, learningProfiles, createdAt);
        addConversationTasks(tasks, ownerUserId, workspaceId, activeCards, conversationSummaries, createdAt);
        return tasks.stream().map(task -> withScheduledDate(task, request.planDate())).toList();
    }

    private void addProfileTasks(
            List<DailyStudyTask> tasks,
            Long ownerUserId,
            Long workspaceId,
            List<StudyFlashcard> cards,
            List<LearningTopicProfile> profiles,
            OffsetDateTime createdAt
    ) {
        profiles.stream()
                .filter(profile -> profile.level() == LearningTopicLevel.WEAK
                        || profile.level() == LearningTopicLevel.AT_RISK)
                .limit(3)
                .forEach(profile -> {
                    List<Long> ids = cards.stream()
                            .filter(card -> card.topic() != null && card.topic().equalsIgnoreCase(profile.topic()))
                            .sorted(Comparator.comparingInt(StudyFlashcard::lapseCount).reversed()
                                    .thenComparing(StudyFlashcard::dueAt))
                            .limit(20).map(StudyFlashcard::id).toList();
                    if (!ids.isEmpty()) {
                        tasks.add(task(
                                ownerUserId, workspaceId, DailyStudyTaskType.MASTERY_REINFORCEMENT,
                                DailyStudyTaskPriority.HIGH, profile.topic(), null,
                                "学习画像掌握度为" + profile.masteryScore() + "，遗忘率为" + profile.lapseRate() + "%",
                                ids, createdAt
                        ));
                    }
                });
    }

    private void addConversationTasks(
            List<DailyStudyTask> tasks,
            Long ownerUserId,
            Long workspaceId,
            List<StudyFlashcard> cards,
            List<ConversationLearningSummary> summaries,
            OffsetDateTime createdAt
    ) {
        summaries.stream()
                .flatMap(summary -> summary.weakTopics().stream())
                .distinct()
                .limit(3)
                .forEach(topic -> {
                    List<Long> ids = cards.stream()
                            .filter(card -> card.topic() != null && card.topic().equalsIgnoreCase(topic))
                            .limit(20).map(StudyFlashcard::id).toList();
                    if (!ids.isEmpty()) {
                        tasks.add(task(
                                ownerUserId, workspaceId, DailyStudyTaskType.CONVERSATION_REVIEW,
                                DailyStudyTaskPriority.HIGH, topic, null,
                                "近期会话中明确表达了该主题的理解困难", ids, createdAt
                        ));
                    }
                });
    }

    private void addDueTask(
            List<DailyStudyTask> tasks,
            Long ownerUserId,
            Long workspaceId,
            List<StudyFlashcard> cards,
            SaveDailyStudyPlanRequest request,
            OffsetDateTime planDeadline,
            OffsetDateTime createdAt
    ) {
        List<Long> ids = cards.stream()
                .filter(card -> !card.dueAt().isAfter(planDeadline))
                .sorted(Comparator.comparing(StudyFlashcard::dueAt).thenComparing(StudyFlashcard::id))
                .limit(request.dailyReviewTarget())
                .map(StudyFlashcard::id)
                .toList();
        if (!ids.isEmpty()) {
            tasks.add(task(
                    ownerUserId, workspaceId, DailyStudyTaskType.DUE_REVIEW, DailyStudyTaskPriority.HIGH,
                    null, null, "优先完成计划日期前到期的复习卡片", ids, createdAt
            ));
        }
    }

    private void addWeakPointTasks(
            List<DailyStudyTask> tasks,
            Long ownerUserId,
            Long workspaceId,
            List<StudyFlashcard> cards,
            OffsetDateTime createdAt
    ) {
        Map<String, List<StudyFlashcard>> byTopic = new LinkedHashMap<>();
        cards.stream()
                .filter(card -> card.lapseCount() > 0)
                .sorted(Comparator.comparingInt(StudyFlashcard::lapseCount).reversed()
                        .thenComparing(StudyFlashcard::dueAt))
                .forEach(card -> byTopic.computeIfAbsent(displayTopic(card.topic()), ignored -> new ArrayList<>())
                        .add(card));
        byTopic.entrySet().stream().limit(MAX_WEAK_TOPICS).forEach(entry -> {
            List<Long> ids = entry.getValue().stream().limit(20).map(StudyFlashcard::id).toList();
            int maxLapses = entry.getValue().stream().mapToInt(StudyFlashcard::lapseCount).max().orElse(0);
            tasks.add(task(
                    ownerUserId, workspaceId, DailyStudyTaskType.WEAK_POINT_REVIEW,
                    DailyStudyTaskPriority.HIGH, entry.getKey(), null,
                    "该主题存在历史回忆失败，最高遗忘次数为" + maxLapses, ids, createdAt
            ));
        });
    }

    private void addPreferredTopicTasks(
            List<DailyStudyTask> tasks,
            Long ownerUserId,
            Long workspaceId,
            List<StudyFlashcard> cards,
            SaveDailyStudyPlanRequest request,
            OffsetDateTime createdAt
    ) {
        normalizeTopics(request.preferredTopics()).forEach(topic -> {
            List<Long> ids = cards.stream()
                    .filter(card -> card.topic() != null && card.topic().equalsIgnoreCase(topic))
                    .map(StudyFlashcard::id)
                    .limit(30)
                    .toList();
            if (!ids.isEmpty()) {
                tasks.add(task(
                        ownerUserId, workspaceId, DailyStudyTaskType.TOPIC_REVIEW,
                        DailyStudyTaskPriority.MEDIUM, topic, null,
                        "根据用户指定的学习主题生成", ids, createdAt
                ));
            }
        });
    }

    private void addDocumentTasks(
            List<DailyStudyTask> tasks,
            Long ownerUserId,
            Long workspaceId,
            List<StudyFlashcard> cards,
            SaveDailyStudyPlanRequest request,
            OffsetDateTime createdAt
    ) {
        safeDocumentIds(request.sourceDocumentIds()).forEach(documentId -> {
            List<Long> ids = cards.stream()
                    .filter(card -> Objects.equals(documentId, card.sourceDocumentId()))
                    .map(StudyFlashcard::id)
                    .limit(30)
                    .toList();
            if (!ids.isEmpty()) {
                tasks.add(task(
                        ownerUserId, workspaceId, DailyStudyTaskType.DOCUMENT_REVIEW,
                        DailyStudyTaskPriority.MEDIUM, null, documentId,
                        "根据用户指定的知识文档生成", ids, createdAt
                ));
            }
        });
    }

    private DailyStudyTask task(
            Long ownerUserId,
            Long workspaceId,
            DailyStudyTaskType type,
            DailyStudyTaskPriority priority,
            String topic,
            Long sourceDocumentId,
            String reason,
            List<Long> flashcardIds,
            OffsetDateTime createdAt
    ) {
        return new DailyStudyTask(
                null, null, ownerUserId, workspaceId, type, priority,
                DailyStudyTaskStatus.PENDING, null, topic, sourceDocumentId,
                flashcardIds.size(), reason, flashcardIds, null, null, null, null,
                0, createdAt, createdAt
        );
    }

    private DailyStudyTask withScheduledDate(DailyStudyTask task, java.time.LocalDate scheduledDate) {
        return new DailyStudyTask(
                task.id(), task.planId(), task.ownerUserId(), task.workspaceId(), task.type(), task.priority(),
                task.status(), scheduledDate, task.topic(), task.sourceDocumentId(), task.targetCardCount(),
                task.reason(), task.flashcardIds(), task.feedbackScore(), task.feedbackComment(),
                task.completedAt(), task.skippedAt(), task.version(), task.createdAt(), task.updatedAt()
        );
    }

    private List<String> normalizeTopics(List<String> topics) {
        if (topics == null) {
            return List.of();
        }
        return topics.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> value.length() <= 100 ? value : value.substring(0, 100))
                .distinct()
                .toList();
    }

    private List<Long> safeDocumentIds(List<Long> documentIds) {
        return documentIds == null ? List.of() : documentIds.stream()
                .filter(Objects::nonNull)
                .filter(value -> value > 0)
                .distinct()
                .toList();
    }

    private String displayTopic(String topic) {
        return topic == null || topic.isBlank() ? "未分类薄弱知识" : topic.trim();
    }
}
