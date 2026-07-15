package com.agentmind.agent.tool;

import com.agentmind.agent.audit.model.AgentToolType;
import com.agentmind.agent.tool.model.AgentToolDefinition;
import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.agent.tool.model.AgentToolExecutionResult;
import com.agentmind.study.flashcard.model.dto.StudyFlashcardResponse;
import com.agentmind.study.flashcard.service.StudyFlashcardApplicationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * 生成并保存复习卡片的写工具。
 *
 * <p>模型负责生成问题、答案和解释，工具只在用户确认后持久化这些内容。</p>
 */
@Component
public class CreateFlashcardAgentTool implements AgentTool {

    public static final String TOOL_NAME = "flashcard.create";
    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "question": {"type": "string", "description": "复习卡片正面问题", "maxLength": 500},
                "answer": {"type": "string", "description": "复习卡片背面答案", "maxLength": 10000},
                "explanation": {"type": "string", "description": "可选的解释、易错点或记忆提示", "maxLength": 10000}
                ,"sourceDocumentId": {"type": "integer", "description": "可选的来源文档编号", "minimum": 1}
                ,"topic": {"type": "string", "description": "可选的知识主题", "maxLength": 100}
              },
              "required": ["question", "answer"],
              "additionalProperties": false
            }
            """;

    private final StudyFlashcardApplicationService flashcardApplicationService;
    private final ObjectMapper objectMapper;

    public CreateFlashcardAgentTool(
            StudyFlashcardApplicationService flashcardApplicationService,
            ObjectMapper objectMapper
    ) {
        this.flashcardApplicationService = flashcardApplicationService;
        this.objectMapper = objectMapper;
    }

    @Override
    public AgentToolDefinition definition() {
        return new AgentToolDefinition(
                TOOL_NAME,
                "在当前知识空间保存一张模型生成的复习卡片，执行前必须由用户确认",
                AgentToolType.WRITE,
                INPUT_SCHEMA
        );
    }

    @Override
    public void validateArguments(JsonNode arguments) {
        AgentToolArgumentReader.requireText(arguments, "question", 500);
        AgentToolArgumentReader.requireText(arguments, "answer", 10_000);
        AgentToolArgumentReader.optionalText(arguments, "explanation", 10_000);
        optionalPositiveLong(arguments, "sourceDocumentId");
        AgentToolArgumentReader.optionalText(arguments, "topic", 100);
    }

    @Override
    public AgentToolExecutionResult execute(AgentToolExecutionContext context, JsonNode arguments) {
        StudyFlashcardResponse flashcard = flashcardApplicationService.createFromAgent(
                context,
                AgentToolArgumentReader.requireText(arguments, "question", 500),
                AgentToolArgumentReader.requireText(arguments, "answer", 10_000),
                AgentToolArgumentReader.optionalText(arguments, "explanation", 10_000),
                optionalPositiveLong(arguments, "sourceDocumentId"),
                AgentToolArgumentReader.optionalText(arguments, "topic", 100)
        );
        return new AgentToolExecutionResult(
                objectMapper.valueToTree(flashcard),
                "复习卡片创建完成，卡片ID=" + flashcard.id()
        );
    }

    private Long optionalPositiveLong(JsonNode arguments, String fieldName) {
        JsonNode value = arguments.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        return AgentToolArgumentReader.requirePositiveLong(arguments, fieldName);
    }
}
