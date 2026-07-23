package com.agentmind.study.flashcard.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentmind.study.flashcard.config.FlashcardGenerationProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

/** 验证真实模型制卡结果的结构化校验和失败降级。 */
class AdaptiveDocumentFlashcardCandidateGeneratorTests {

    @Test
    void shouldKeepSpecificModelCardAndRejectGenericCard() {
        String modelJson = """
                {
                  "cards": [
                    {
                      "sourceChunkId": "1-0",
                      "topic": "CAS",
                      "question": "本文核心内容是什么？",
                      "answer": "宽泛答案",
                      "explanation": "无"
                    },
                    {
                      "sourceChunkId": "1-0",
                      "topic": "CAS",
                      "question": "CAS 操作包含哪三个步骤？",
                      "answer": "读取旧值、比较期望值，并在相等时写入新值。",
                      "explanation": "用于检查 CAS 的基本过程。"
                    }
                  ]
                }
                """;
        AdaptiveDocumentFlashcardCandidateGenerator generator = generator(prompt -> new ChatResponse(
                List.of(new Generation(new AssistantMessage(modelJson)))
        ));

        List<GeneratedDocumentFlashcard> cards = generator.generate(sources(), 1);

        assertThat(cards).singleElement().satisfies(card -> {
            assertThat(card.question()).isEqualTo("CAS 操作包含哪三个步骤？");
            assertThat(card.answer()).doesNotContain("宽泛答案");
            assertThat(card.sourceChunkId()).isEqualTo("1-0");
        });
    }

    @Test
    void shouldFallbackWithoutSavingModelFailureText() {
        AdaptiveDocumentFlashcardCandidateGenerator generator = generator(prompt -> {
            throw new IllegalStateException("模型调用失败，请稍后重试");
        });

        List<GeneratedDocumentFlashcard> cards = generator.generate(sources(), 1);

        assertThat(cards).singleElement().satisfies(card -> {
            assertThat(card.question()).isEqualTo("CAS是什么？");
            assertThat(card.answer()).doesNotContain("模型调用失败", "请稍后重试");
        });
    }

    private AdaptiveDocumentFlashcardCandidateGenerator generator(ChatModel chatModel) {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("testChatModel", chatModel);
        FlashcardGenerationProperties properties = new FlashcardGenerationProperties();
        properties.setProvider("spring-ai");
        properties.setModelName("test-model");
        return new AdaptiveDocumentFlashcardCandidateGenerator(
                beanFactory.getBeanProvider(ChatModel.class),
                new LocalDocumentFlashcardCandidateGenerator(),
                properties,
                new ObjectMapper()
        );
    }

    private List<DocumentFlashcardSource> sources() {
        return List.of(new DocumentFlashcardSource(
                1L,
                "并发编程",
                "1-0",
                "CAS",
                "CAS 是一种基于比较并交换的无锁原子更新机制。"
        ));
    }
}
