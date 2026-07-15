package com.agentmind.study.flashcard.algorithm;

import com.agentmind.study.flashcard.model.StudyFlashcardReview;
import com.agentmind.study.flashcard.model.StudyFlashcardSchedule;
import com.agentmind.study.flashcard.fsrs.model.FsrsCardSnapshot;
import com.agentmind.study.flashcard.fsrs.model.FsrsUserProfile;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 支持持久化内部算法状态的间隔重复算法端口。
 *
 * <p>普通算法只需要返回通用调度字段；FSRS 还需要稳定性、难度、学习状态等内部数据。
 * 通过独立端口返回快照，可以避免把第三方库对象泄漏到领域模型和数据库适配器。</p>
 */
public interface StatefulSpacedRepetitionAlgorithm extends SpacedRepetitionAlgorithm {

    StatefulCalculation calculateWithSnapshot(
            Long flashcardId,
            StudyFlashcardSchedule current,
            int score,
            OffsetDateTime reviewedAt,
            List<StudyFlashcardReview> history,
            Optional<FsrsCardSnapshot> currentSnapshot,
            FsrsUserProfile userProfile
    );

    /** 算法计算结果同时携带通用排期和下一版持久化快照。 */
    record StatefulCalculation(
            StudyFlashcardSchedule schedule,
            String snapshotPayload,
            int snapshotSchemaVersion
    ) {
    }
}
