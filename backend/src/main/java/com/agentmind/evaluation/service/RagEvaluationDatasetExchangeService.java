package com.agentmind.evaluation.service;

import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.common.exception.BusinessException;
import com.agentmind.common.exception.ErrorCode;
import com.agentmind.evaluation.config.RagEvaluationProperties;
import com.agentmind.evaluation.model.RagEvaluationCase;
import com.agentmind.evaluation.model.RagEvaluationDataset;
import com.agentmind.evaluation.model.RagEvaluationDatasetVersion;
import com.agentmind.evaluation.model.RagEvaluationExchangeFormat;
import com.agentmind.evaluation.model.dto.CreateRagEvaluationDatasetRequest;
import com.agentmind.evaluation.model.dto.RagEvaluationCaseRequest;
import com.agentmind.evaluation.model.dto.RagEvaluationDatasetExchangeDocument;
import com.agentmind.evaluation.model.dto.RagEvaluationDatasetVersionResponse;
import com.agentmind.evaluation.model.dto.RagEvaluationExportFile;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;

/**
 * 评估集 JSON/CSV 交换服务。
 *
 * <p>列表字段在 CSV 单元格内使用 JSON 数组表示，避免自行拆分逗号导致片段编号或关键词损坏。
 * 导入后仍统一进入评估集应用服务，因此数量限制、题目标识唯一性和可回答题规则不会被绕过。</p>
 */
@Service
public class RagEvaluationDatasetExchangeService {

    private static final CSVFormat CSV_FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader("name", "description", "changeNote", "caseKey", "question",
                    "expectedRelevantChunkIds", "expectedRelevantDocumentIds", "expectedRefusal",
                    "expectedAnswerKeywords")
            .setSkipHeaderRecord(false)
            .build();

    private final RagEvaluationDatasetService datasetService;
    private final RagEvaluationProperties properties;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public RagEvaluationDatasetExchangeService(
            RagEvaluationDatasetService datasetService,
            RagEvaluationProperties properties,
            ObjectMapper objectMapper,
            Validator validator
    ) {
        this.datasetService = datasetService;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    public RagEvaluationDatasetVersionResponse importDataset(
            AgentToolExecutionContext context,
            RagEvaluationExchangeFormat format,
            byte[] content
    ) {
        validateSize(content);
        RagEvaluationDatasetExchangeDocument document = switch (format) {
            case JSON -> readJson(content);
            case CSV -> readCsv(content);
        };
        validateDocument(document);
        return datasetService.create(context, new CreateRagEvaluationDatasetRequest(
                document.name(), document.description(), document.cases()
        ));
    }

    public RagEvaluationExportFile exportDataset(
            AgentToolExecutionContext context,
            Long datasetId,
            int version,
            RagEvaluationExchangeFormat format
    ) {
        RagEvaluationDataset dataset = datasetService.requireDataset(context, datasetId);
        RagEvaluationDatasetVersion datasetVersion = datasetService.requireVersion(context, datasetId, version);
        RagEvaluationDatasetExchangeDocument document = new RagEvaluationDatasetExchangeDocument(
                dataset.name(), dataset.description(), datasetVersion.changeNote(),
                datasetVersion.cases().stream().map(this::toRequest).toList()
        );
        String baseName = sanitizeFileName(dataset.name()) + "-v" + version;
        return switch (format) {
            case JSON -> new RagEvaluationExportFile(
                    baseName + ".json", "application/json;charset=UTF-8", writeJson(document)
            );
            case CSV -> new RagEvaluationExportFile(
                    baseName + ".csv", "text/csv;charset=UTF-8", writeCsv(document)
            );
        };
    }

    private RagEvaluationDatasetExchangeDocument readJson(byte[] content) {
        try {
            return objectMapper.readValue(content, RagEvaluationDatasetExchangeDocument.class);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "评估集JSON格式不合法");
        }
    }

    private RagEvaluationDatasetExchangeDocument readCsv(byte[] content) {
        int offset = content.length >= 3
                && content[0] == (byte) 0xEF && content[1] == (byte) 0xBB && content[2] == (byte) 0xBF ? 3 : 0;
        try (CSVParser parser = CSV_FORMAT.builder().setHeader().setSkipHeaderRecord(true).build().parse(
                new InputStreamReader(
                        new ByteArrayInputStream(content, offset, content.length - offset), StandardCharsets.UTF_8
                ))) {
            List<RagEvaluationCaseRequest> cases = new ArrayList<>();
            String name = null;
            String description = "";
            String changeNote = "";
            for (CSVRecord record : parser) {
                if (name == null) {
                    name = record.get("name");
                    description = record.get("description");
                    changeNote = record.get("changeNote");
                }
                cases.add(new RagEvaluationCaseRequest(
                        record.get("caseKey"), record.get("question"),
                        readList(record.get("expectedRelevantChunkIds"), new TypeReference<>() {
                        }),
                        readList(record.get("expectedRelevantDocumentIds"), new TypeReference<>() {
                        }),
                        Boolean.parseBoolean(record.get("expectedRefusal")),
                        readList(record.get("expectedAnswerKeywords"), new TypeReference<>() {
                        })
                ));
            }
            return new RagEvaluationDatasetExchangeDocument(name, description, changeNote, cases);
        } catch (IOException | IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "评估集CSV格式不合法");
        }
    }

    private byte[] writeJson(RagEvaluationDatasetExchangeDocument document) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(document);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("导出评估集JSON失败", exception);
        }
    }

    private byte[] writeCsv(RagEvaluationDatasetExchangeDocument document) {
        try (StringWriter writer = new StringWriter(); CSVPrinter printer = new CSVPrinter(writer, CSV_FORMAT)) {
            for (RagEvaluationCaseRequest evaluationCase : document.cases()) {
                printer.printRecord(
                        document.name(), document.description(), document.changeNote(),
                        evaluationCase.caseKey(), evaluationCase.question(),
                        objectMapper.writeValueAsString(orEmpty(evaluationCase.expectedRelevantChunkIds())),
                        objectMapper.writeValueAsString(orEmpty(evaluationCase.expectedRelevantDocumentIds())),
                        evaluationCase.expectedRefusal(),
                        objectMapper.writeValueAsString(orEmpty(evaluationCase.expectedAnswerKeywords()))
                );
            }
            printer.flush();
            // UTF-8 BOM 便于 Windows Excel 正确识别中文，同时 Commons CSV 仍负责字段转义。
            byte[] body = writer.toString().getBytes(StandardCharsets.UTF_8);
            byte[] output = new byte[body.length + 3];
            output[0] = (byte) 0xEF;
            output[1] = (byte) 0xBB;
            output[2] = (byte) 0xBF;
            System.arraycopy(body, 0, output, 3, body.length);
            return output;
        } catch (IOException exception) {
            throw new IllegalStateException("导出评估集CSV失败", exception);
        }
    }

    private <T> List<T> readList(String json, TypeReference<List<T>> type) throws JsonProcessingException {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return objectMapper.readValue(json, type);
    }

    private void validateDocument(RagEvaluationDatasetExchangeDocument document) {
        Set<ConstraintViolation<RagEvaluationDatasetExchangeDocument>> violations = validator.validate(document);
        if (!violations.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, violations.iterator().next().getMessage());
        }
    }

    private void validateSize(byte[] content) {
        if (content == null || content.length == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "导入文件不能为空");
        }
        if (content.length > properties.getMaximumImportBytes()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "导入文件超过允许大小");
        }
    }

    private RagEvaluationCaseRequest toRequest(RagEvaluationCase value) {
        return new RagEvaluationCaseRequest(
                value.caseKey(), value.question(), value.expectedRelevantChunkIds(),
                value.expectedRelevantDocumentIds(), value.expectedRefusal(), value.expectedAnswerKeywords()
        );
    }

    private List<?> orEmpty(List<?> values) {
        return values == null ? List.of() : values;
    }

    private String sanitizeFileName(String name) {
        String sanitized = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return sanitized.isBlank() ? "rag-evaluation-dataset" : sanitized;
    }
}
