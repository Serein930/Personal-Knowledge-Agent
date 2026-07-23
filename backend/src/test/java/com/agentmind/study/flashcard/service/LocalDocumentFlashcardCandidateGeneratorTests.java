package com.agentmind.study.flashcard.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** 验证离线制卡策略生成单知识点问题和短答案。 */
class LocalDocumentFlashcardCandidateGeneratorTests {

    private final LocalDocumentFlashcardCandidateGenerator generator =
            new LocalDocumentFlashcardCandidateGenerator();

    @Test
    void shouldSplitFactsIntoSpecificQuestionAnswerCards() {
        List<GeneratedDocumentFlashcard> cards = generator.generate(List.of(
                new DocumentFlashcardSource(
                        1L,
                        "线程池笔记",
                        "1-0",
                        "核心参数",
                        "核心线程数决定常驻工作线程数量。最大线程数用于限制线程池可扩展的线程上限。"
                )
        ), 2);

        assertThat(cards).hasSize(2);
        assertThat(cards).extracting(GeneratedDocumentFlashcard::question)
                .contains("核心线程数决定什么？", "最大线程数用于解决什么问题？");
        assertThat(cards).allMatch(card -> card.answer().length() <= 260);
        assertThat(cards).allMatch(card -> card.sourceChunkId().equals("1-0"));
    }

    @Test
    void shouldIgnoreQuestionOutlineWithoutGroundedAnswer() {
        List<GeneratedDocumentFlashcard> cards = generator.generate(List.of(
                new DocumentFlashcardSource(
                        1L,
                        "面试题纲",
                        "1-0",
                        "并发编程",
                        "什么是 CAS？\nReentrantLock 与 synchronized 有什么区别？"
                )
        ), 5);

        assertThat(cards).isEmpty();
    }
}
