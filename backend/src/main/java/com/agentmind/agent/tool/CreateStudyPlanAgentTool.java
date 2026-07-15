package com.agentmind.agent.tool;

import com.agentmind.agent.audit.model.AgentToolType;
import com.agentmind.agent.tool.model.AgentToolDefinition;
import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.agent.tool.model.AgentToolExecutionResult;
import com.agentmind.common.exception.BusinessException;
import com.agentmind.common.exception.ErrorCode;
import com.agentmind.study.plan.model.dto.DailyStudyPlanResponse;
import com.agentmind.study.plan.model.dto.SaveDailyStudyPlanRequest;
import com.agentmind.study.plan.service.DailyStudyPlanApplicationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 创建个性化每日学习计划的写工具。
 *
 * <p>工具只接收用户偏好，任务卡片关联和薄弱点优先级由服务端生成器决定。它属于写工具，
 * 必须经过确认单、令牌复核、幂等审计和事务执行通道，模型不能直接创建计划。</p>
 */
@Component
public class CreateStudyPlanAgentTool implements AgentTool {

    public static final String TOOL_NAME = "study_plan.create";
    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "planDate": {"type": "string", "description": "计划日期，格式为YYYY-MM-DD"},
                "dailyReviewTarget": {"type": "integer", "minimum": 1, "maximum": 500},
                "preferredTopics": {
                  "type": "array", "maxItems": 10,
                  "items": {"type": "string", "maxLength": 100}
                },
                "sourceDocumentIds": {
                  "type": "array", "maxItems": 20,
                  "items": {"type": "integer", "minimum": 1}
                }
              },
              "required": ["planDate", "dailyReviewTarget"],
              "additionalProperties": false
            }
            """;

    private final DailyStudyPlanApplicationService planService;
    private final ObjectMapper objectMapper;

    public CreateStudyPlanAgentTool(
            DailyStudyPlanApplicationService planService,
            ObjectMapper objectMapper
    ) {
        this.planService = planService;
        this.objectMapper = objectMapper;
    }

    @Override
    public AgentToolDefinition definition() {
        return new AgentToolDefinition(
                TOOL_NAME,
                "在当前知识空间创建个性化每日学习计划，执行前必须由用户确认",
                AgentToolType.WRITE,
                INPUT_SCHEMA
        );
    }

    @Override
    public void validateArguments(JsonNode arguments) {
        parseDate(arguments);
        AgentToolArgumentReader.optionalInteger(arguments, "dailyReviewTarget", 0, 1, 500);
        readTopics(arguments);
        readDocumentIds(arguments);
    }

    @Override
    public AgentToolExecutionResult execute(AgentToolExecutionContext context, JsonNode arguments) {
        DailyStudyPlanResponse plan = planService.save(context, new SaveDailyStudyPlanRequest(
                parseDate(arguments),
                AgentToolArgumentReader.optionalInteger(arguments, "dailyReviewTarget", 0, 1, 500),
                readTopics(arguments),
                readDocumentIds(arguments)
        ));
        return new AgentToolExecutionResult(
                objectMapper.valueToTree(plan),
                "每日学习计划创建完成，计划ID=" + plan.id()
        );
    }

    private LocalDate parseDate(JsonNode arguments) {
        String value = AgentToolArgumentReader.requireText(arguments, "planDate", 10);
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "planDate必须使用YYYY-MM-DD格式");
        }
    }

    private List<String> readTopics(JsonNode arguments) {
        JsonNode node = optionalArray(arguments, "preferredTopics", 10);
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isTextual() || item.asText().isBlank() || item.asText().trim().length() > 100) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "preferredTopics必须是非空短文本数组");
            }
            values.add(item.asText().trim());
        }
        return List.copyOf(values);
    }

    private List<Long> readDocumentIds(JsonNode arguments) {
        JsonNode node = optionalArray(arguments, "sourceDocumentIds", 20);
        List<Long> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isIntegralNumber() || !item.canConvertToLong() || item.asLong() <= 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "sourceDocumentIds必须是正整数数组");
            }
            values.add(item.asLong());
        }
        return List.copyOf(values);
    }

    private JsonNode optionalArray(JsonNode arguments, String fieldName, int maxItems) {
        AgentToolArgumentReader.requireObject(arguments);
        JsonNode node = arguments.get(fieldName);
        if (node == null || node.isNull()) {
            return objectMapper.createArrayNode();
        }
        if (!node.isArray() || node.size() > maxItems) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, fieldName + "必须是最多" + maxItems + "项的数组");
        }
        return node;
    }
}
