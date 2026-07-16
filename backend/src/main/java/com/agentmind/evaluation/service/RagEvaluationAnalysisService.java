package com.agentmind.evaluation.service;

import com.agentmind.agent.service.AgentToolExecutionAuthorizer;
import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.evaluation.model.RagEvaluationCase;
import com.agentmind.evaluation.model.RagEvaluationCaseDiffType;
import com.agentmind.evaluation.model.RagEvaluationDatasetVersion;
import com.agentmind.evaluation.model.RagEvaluationJob;
import com.agentmind.evaluation.model.RagEvaluationQualityGateStatus;
import com.agentmind.evaluation.model.dto.RagEvaluationCaseDiffResponse;
import com.agentmind.evaluation.model.dto.RagEvaluationTrendPointResponse;
import com.agentmind.evaluation.model.dto.RagEvaluationTrendResponse;
import com.agentmind.evaluation.model.dto.RagEvaluationVersionDiffResponse;
import com.agentmind.evaluation.repository.RagEvaluationJobRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/** 评估指标趋势和数据集逐题版本差异查询服务。 */
@Service
public class RagEvaluationAnalysisService {

    private final RagEvaluationDatasetService datasetService;
    private final RagEvaluationJobRepository jobRepository;
    private final AgentToolExecutionAuthorizer authorizer;

    public RagEvaluationAnalysisService(
            RagEvaluationDatasetService datasetService,
            RagEvaluationJobRepository jobRepository,
            AgentToolExecutionAuthorizer authorizer
    ) {
        this.datasetService = datasetService;
        this.jobRepository = jobRepository;
        this.authorizer = authorizer;
    }

    public RagEvaluationTrendResponse trend(
            AgentToolExecutionContext context,
            Long datasetId,
            Integer datasetVersion,
            int limit
    ) {
        authorizer.authorize(context);
        datasetService.requireDataset(context, datasetId);
        if (datasetVersion != null) {
            datasetService.requireVersion(context, datasetId, datasetVersion);
        }
        List<RagEvaluationJob> jobs = new ArrayList<>(jobRepository.findSuccessfulByDataset(
                context.ownerUserId(), context.workspaceId(), datasetId, datasetVersion, limit
        ));
        Collections.reverse(jobs);
        return new RagEvaluationTrendResponse(datasetId, datasetVersion, jobs.stream().map(job ->
                new RagEvaluationTrendPointResponse(
                        job.id(), job.datasetVersion(), job.experimentConfig().experimentName(),
                        job.experimentConfig().retrievalStrategy().name(),
                        job.experimentConfig().rerankStrategy().name(), job.metrics(),
                        job.qualityGateResult() == null
                                ? RagEvaluationQualityGateStatus.NOT_CONFIGURED : job.qualityGateResult().status(),
                        job.completedAt()
                )).toList());
    }

    public RagEvaluationVersionDiffResponse versionDiff(
            AgentToolExecutionContext context,
            Long datasetId,
            int fromVersion,
            int toVersion
    ) {
        authorizer.authorize(context);
        RagEvaluationDatasetVersion beforeVersion = datasetService.requireVersion(context, datasetId, fromVersion);
        RagEvaluationDatasetVersion afterVersion = datasetService.requireVersion(context, datasetId, toVersion);
        Map<String, RagEvaluationCase> before = index(beforeVersion.cases());
        Map<String, RagEvaluationCase> after = index(afterVersion.cases());
        Set<String> keys = new LinkedHashSet<>(before.keySet());
        keys.addAll(after.keySet());
        List<RagEvaluationCaseDiffResponse> differences = keys.stream().map(key -> {
            RagEvaluationCase oldCase = before.get(key);
            RagEvaluationCase newCase = after.get(key);
            RagEvaluationCaseDiffType type;
            if (oldCase == null) {
                type = RagEvaluationCaseDiffType.ADDED;
            } else if (newCase == null) {
                type = RagEvaluationCaseDiffType.REMOVED;
            } else if (oldCase.equals(newCase)) {
                type = RagEvaluationCaseDiffType.UNCHANGED;
            } else {
                type = RagEvaluationCaseDiffType.MODIFIED;
            }
            return new RagEvaluationCaseDiffResponse(key, type, oldCase, newCase);
        }).toList();
        return new RagEvaluationVersionDiffResponse(
                datasetId, fromVersion, toVersion,
                count(differences, RagEvaluationCaseDiffType.ADDED),
                count(differences, RagEvaluationCaseDiffType.REMOVED),
                count(differences, RagEvaluationCaseDiffType.MODIFIED),
                count(differences, RagEvaluationCaseDiffType.UNCHANGED),
                differences
        );
    }

    private Map<String, RagEvaluationCase> index(List<RagEvaluationCase> cases) {
        Map<String, RagEvaluationCase> result = new LinkedHashMap<>();
        cases.forEach(value -> result.put(value.caseKey(), value));
        return result;
    }

    private int count(List<RagEvaluationCaseDiffResponse> cases, RagEvaluationCaseDiffType type) {
        return (int) cases.stream().filter(value -> value.type() == type).count();
    }
}
