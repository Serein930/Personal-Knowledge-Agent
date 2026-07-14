package com.agentmind.agent.tool;

import com.agentmind.agent.audit.model.AgentToolType;
import com.agentmind.agent.tool.model.AgentToolDefinition;
import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.agent.tool.model.AgentToolExecutionResult;
import com.agentmind.knowledge.model.dto.KnowledgeSearchResponse;
import com.agentmind.knowledge.service.KnowledgeSearchService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * 在当前知识空间中执行语义检索的只读工具。
 */
@Component
public class KnowledgeSearchAgentTool implements AgentTool {

    public static final String TOOL_NAME = "knowledge.search";
    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "query": {
                  "type": "string",
                  "description": "需要在当前知识空间中检索的问题或关键词"
                },
                "topK": {
                  "type": "integer",
                  "description": "最多返回的相关片段数量",
                  "minimum": 1,
                  "maximum": 20
                }
              },
              "required": ["query"],
              "additionalProperties": false
            }
            """;

    private final KnowledgeSearchService knowledgeSearchService;
    private final ObjectMapper objectMapper;

    public KnowledgeSearchAgentTool(KnowledgeSearchService knowledgeSearchService, ObjectMapper objectMapper) {
        this.knowledgeSearchService = knowledgeSearchService;
        this.objectMapper = objectMapper;
    }

    @Override
    public AgentToolDefinition definition() {
        return new AgentToolDefinition(
                TOOL_NAME,
                "在当前知识空间中检索与问题相关的知识片段",
                AgentToolType.READ,
                INPUT_SCHEMA
        );
    }

    @Override
    public AgentToolExecutionResult execute(AgentToolExecutionContext context, JsonNode arguments) {
        String query = AgentToolArgumentReader.requireText(arguments, "query", 1000);
        int topK = AgentToolArgumentReader.optionalInteger(arguments, "topK", 5, 1, 20);
        KnowledgeSearchResponse response = knowledgeSearchService.search(context.workspaceId(), query, topK);
        return new AgentToolExecutionResult(
                objectMapper.valueToTree(response),
                "知识检索完成，返回" + response.results().size() + "个片段"
        );
    }
}
