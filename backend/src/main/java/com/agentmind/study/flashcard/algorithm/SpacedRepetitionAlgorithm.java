package com.agentmind.study.flashcard.algorithm;

import com.agentmind.study.flashcard.model.StudyFlashcardSchedule;
import java.time.OffsetDateTime;

/**
 * 间隔重复算法端口。
 */
public interface SpacedRepetitionAlgorithm {

    /** 返回稳定算法标识，写入复习记录以支持后续效果对比。 */
    String name();

    /** 根据当前调度状态和 0 到 5 分的回忆质量计算下一次调度。 */
    StudyFlashcardSchedule calculate(
            StudyFlashcardSchedule current,
            int score,
            OffsetDateTime reviewedAt
    );
}
