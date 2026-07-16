package com.agentmind.evaluation.judge;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentmind.evaluation.config.RagEvaluationProperties;
import com.agentmind.evaluation.metric.RagEvaluationTextSimilarity;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 默认裁判必须可重复，并明确标记分数来源。 */
class DeterministicRagEvaluationAnswerJudgeTests {

    @Test
    void shouldReturnAuditableDeterministicEvidence() {
        RagEvaluationProperties properties = new RagEvaluationProperties();
        DeterministicRagEvaluationAnswerJudge judge = new DeterministicRagEvaluationAnswerJudge(
                new RagEvaluationTextSimilarity(), properties
        );

        RagEvaluationJudgeResult result = judge.judge(new RagEvaluationJudgeRequest(
                "虚拟线程适合什么场景？",
                "虚拟线程适合大量阻塞任务。",
                "资料指出虚拟线程适合处理大量阻塞任务。",
                List.of("阻塞")
        ));

        assertThat(result.faithfulness()).isGreaterThan(0);
        assertThat(result.answerRelevance()).isGreaterThan(0);
        assertThat(result.evidence().judgeType()).isEqualTo("deterministic");
        assertThat(result.evidence().promptVersion()).isEqualTo("rag-judge-v1");
        assertThat(result.evidence().fallbackUsed()).isFalse();
    }
}
