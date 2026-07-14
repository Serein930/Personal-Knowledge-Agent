package com.agentmind.agent.tool;

import com.agentmind.agent.audit.model.AgentToolType;
import com.agentmind.agent.tool.model.AgentToolDefinition;
import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.agent.tool.model.AgentToolExecutionResult;
import com.agentmind.common.exception.BusinessException;
import com.agentmind.common.exception.ErrorCode;
import com.agentmind.document.model.dto.DocumentChunkResponse;
import com.agentmind.document.service.DocumentApplicationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * 读取指定文档片段的只读工具。
 *
 * <p>查询始终携带当前知识空间编号，无法通过伪造文档编号读取其他知识空间的内容。</p>
 */
@Component
public class DocumentChunkReadAgentTool implements AgentTool {

    public static final String TOOL_NAME = "document.read_chunk";
    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "documentId": {
                  "type": "integer",
                  "description": "目标文档编号",
                  "minimum": 1
                },
                "chunkId": {
                  "type": "string",
                  "description": "需要读取的文档片段编号"
                }
              },
              "required": ["documentId", "chunkId"],
              "additionalProperties": false
            }
            """;

    private final DocumentApplicationService documentApplicationService;
    private final ObjectMapper objectMapper;

    public DocumentChunkReadAgentTool(
            DocumentApplicationService documentApplicationService,
            ObjectMapper objectMapper
    ) {
        this.documentApplicationService = documentApplicationService;
        this.objectMapper = objectMapper;
    }

    @Override
    public AgentToolDefinition definition() {
        return new AgentToolDefinition(
                TOOL_NAME,
                "读取当前知识空间内指定文档的一个片段",
                AgentToolType.READ,
                INPUT_SCHEMA
        );
    }

    @Override
    public void validateArguments(JsonNode arguments) {
        AgentToolArgumentReader.requirePositiveLong(arguments, "documentId");
        AgentToolArgumentReader.requireText(arguments, "chunkId", 120);
    }

    @Override
    public AgentToolExecutionResult execute(AgentToolExecutionContext context, JsonNode arguments) {
        long documentId = AgentToolArgumentReader.requirePositiveLong(arguments, "documentId");
        String chunkId = AgentToolArgumentReader.requireText(arguments, "chunkId", 120);
        DocumentChunkResponse chunk = documentApplicationService.listDocumentChunks(context.workspaceId(), documentId)
                .stream()
                .filter(candidate -> chunkId.equals(candidate.id()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "文档片段不存在"));
        return new AgentToolExecutionResult(
                objectMapper.valueToTree(chunk),
                "文档片段读取完成，文档ID=" + documentId + "，片段ID=" + chunk.id()
        );
    }
}
