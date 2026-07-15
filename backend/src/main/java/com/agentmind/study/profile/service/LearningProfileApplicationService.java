package com.agentmind.study.profile.service;

import com.agentmind.agent.service.AgentToolExecutionAuthorizer;
import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.study.flashcard.model.StudyFlashcard;
import com.agentmind.study.flashcard.model.StudyFlashcardReview;
import com.agentmind.study.flashcard.repository.StudyFlashcardRepository;
import com.agentmind.study.flashcard.repository.StudyFlashcardReviewRepository;
import com.agentmind.study.profile.model.LearningTopicLevel;
import com.agentmind.study.profile.model.LearningTopicProfile;
import com.agentmind.study.profile.model.dto.LearningTopicProfileResponse;
import com.agentmind.study.profile.repository.LearningTopicProfileRepository;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** 根据真实卡片和评分事件重建主题学习画像。 */
@Service
public class LearningProfileApplicationService {

    private final LearningTopicProfileRepository repository;
    private final StudyFlashcardRepository flashcardRepository;
    private final StudyFlashcardReviewRepository reviewRepository;
    private final AgentToolExecutionAuthorizer authorizer;

    public LearningProfileApplicationService(
            LearningTopicProfileRepository repository,
            StudyFlashcardRepository flashcardRepository,
            StudyFlashcardReviewRepository reviewRepository,
            AgentToolExecutionAuthorizer authorizer
    ) {
        this.repository = repository;
        this.flashcardRepository = flashcardRepository;
        this.reviewRepository = reviewRepository;
        this.authorizer = authorizer;
    }

    public List<LearningTopicProfileResponse> get(AgentToolExecutionContext context) {
        authorizer.authorize(context);
        return repository.findByScope(context.ownerUserId(), context.workspaceId())
                .stream().map(this::toResponse).toList();
    }

    public List<LearningTopicProfileResponse> refresh(AgentToolExecutionContext context) {
        authorizer.authorize(context);
        return refreshInternal(context.ownerUserId(), context.workspaceId())
                .stream().map(this::toResponse).toList();
    }

    /** 计划生成器复用的内部入口，返回按薄弱程度排序的最新画像。 */
    public List<LearningTopicProfile> refreshInternal(Long ownerUserId, Long workspaceId) {
        List<StudyFlashcard> cards = flashcardRepository.findAllByOwnerUserIdAndWorkspaceId(ownerUserId, workspaceId);
        List<StudyFlashcardReview> reviews = reviewRepository.findAllByOwnerUserIdAndWorkspaceId(ownerUserId, workspaceId);
        Map<Long, StudyFlashcard> cardsById = new HashMap<>();
        cards.forEach(card -> cardsById.put(card.id(), card));
        Map<String, TopicAccumulator> accumulators = new HashMap<>();
        for (StudyFlashcard card : cards) {
            accumulators.computeIfAbsent(normalizeTopic(card.topic()), TopicAccumulator::new).cards.add(card);
        }
        for (StudyFlashcardReview review : reviews) {
            StudyFlashcard card = cardsById.get(review.flashcardId());
            if (card != null) {
                accumulators.computeIfAbsent(normalizeTopic(card.topic()), TopicAccumulator::new).reviews.add(review);
            }
        }
        OffsetDateTime now = OffsetDateTime.now();
        List<LearningTopicProfile> profiles = accumulators.values().stream()
                .map(value -> value.toProfile(ownerUserId, workspaceId, now))
                .sorted(Comparator.comparingDouble(LearningTopicProfile::masteryScore)
                        .thenComparing(LearningTopicProfile::topic))
                .toList();
        repository.replaceScope(ownerUserId, workspaceId, profiles);
        return profiles;
    }

    private String normalizeTopic(String topic) {
        return topic == null || topic.isBlank() ? "未分类知识" : topic.trim();
    }

    private LearningTopicProfileResponse toResponse(LearningTopicProfile profile) {
        return new LearningTopicProfileResponse(
                profile.topic(), profile.cardCount(), profile.reviewCount(), profile.successRate(),
                profile.lapseRate(), profile.masteryScore(), profile.level(),
                profile.lastReviewedAt(), profile.updatedAt()
        );
    }

    private static final class TopicAccumulator {

        private final String topic;
        private final List<StudyFlashcard> cards = new ArrayList<>();
        private final List<StudyFlashcardReview> reviews = new ArrayList<>();

        private TopicAccumulator(String topic) {
            this.topic = topic;
        }

        private LearningTopicProfile toProfile(Long ownerUserId, Long workspaceId, OffsetDateTime now) {
            long successes = reviews.stream().filter(review -> review.score() >= 3).count();
            long lapses = reviews.size() - successes;
            double successRate = reviews.isEmpty() ? 0 : successes * 100.0 / reviews.size();
            double lapseRate = reviews.isEmpty() ? 0 : lapses * 100.0 / reviews.size();
            double matureRatio = cards.isEmpty() ? 0
                    : cards.stream().filter(card -> card.intervalDays() >= 21).count() * 100.0 / cards.size();
            OffsetDateTime lastReviewedAt = reviews.stream().map(StudyFlashcardReview::reviewedAt)
                    .max(OffsetDateTime::compareTo).orElse(null);
            double recency = lastReviewedAt == null ? 0
                    : Math.max(0, 100 - ChronoUnit.DAYS.between(lastReviewedAt, now) * 4.0);
            double mastery = reviews.isEmpty() ? 10
                    : successRate * 0.55 + matureRatio * 0.25 + recency * 0.20;
            mastery = round(Math.max(0, Math.min(100, mastery)));
            return new LearningTopicProfile(
                    ownerUserId, workspaceId, topic, cards.size(), reviews.size(),
                    round(successRate), round(lapseRate), mastery, level(mastery, lapseRate),
                    lastReviewedAt, now
            );
        }

        private LearningTopicLevel level(double mastery, double lapseRate) {
            if (mastery < 40 || lapseRate >= 40) {
                return LearningTopicLevel.WEAK;
            }
            if (mastery < 60 || lapseRate >= 25) {
                return LearningTopicLevel.AT_RISK;
            }
            if (mastery < 80) {
                return LearningTopicLevel.STABLE;
            }
            return LearningTopicLevel.STRONG;
        }

        private double round(double value) {
            return Math.round(value * 100.0) / 100.0;
        }
    }
}
