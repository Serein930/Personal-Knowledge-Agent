package com.agentmind.agent.tool;

import com.agentmind.agent.audit.model.AgentToolType;
import com.agentmind.agent.tool.model.AgentToolDefinition;
import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.agent.tool.model.AgentToolExecutionResult;
import com.agentmind.study.note.model.dto.KnowledgeNoteResponse;
import com.agentmind.study.note.service.KnowledgeNoteApplicationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * 创建知识笔记的首个写工具。
 *
 * <p>该工具不会被 Spring AI 自动工具白名单直接暴露。只有写工具确认服务完成令牌、状态和权限复核后，
 * 才能通过专用执行通道调用。</p>
 */
@Component
public class CreateNoteAgentTool implements AgentTool {

    public static final String TOOL_NAME = "note.create";
    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "title": {
                  "type": "string",
                  "description": "知识笔记标题",
                  "maxLength": 120
                },
                "content": {
                  "type": "string",
                  "description": "知识笔记正文",
                  "maxLength": 20000
                }
              },
              "required": ["title", "content"],
              "additionalProperties": false
            }
            """;

    private final KnowledgeNoteApplicationService noteApplicationService;
    private final ObjectMapper objectMapper;

    public CreateNoteAgentTool(
            KnowledgeNoteApplicationService noteApplicationService,
            ObjectMapper objectMapper
    ) {
        this.noteApplicationService = noteApplicationService;
        this.objectMapper = objectMapper;
    }

    @Override
    public AgentToolDefinition definition() {
        return new AgentToolDefinition(
                TOOL_NAME,
                "在当前知识空间创建一条知识笔记，执行前必须由用户确认",
                AgentToolType.WRITE,
                INPUT_SCHEMA
        );
    }

    @Override
    public void validateArguments(JsonNode arguments) {
        AgentToolArgumentReader.requireText(arguments, "title", 120);
        AgentToolArgumentReader.requireText(arguments, "content", 20_000);
    }

    @Override
    public AgentToolExecutionResult execute(AgentToolExecutionContext context, JsonNode arguments) {
        String title = AgentToolArgumentReader.requireText(arguments, "title", 120);
        String content = AgentToolArgumentReader.requireText(arguments, "content", 20_000);
        KnowledgeNoteResponse note = noteApplicationService.createFromAgent(context, title, content);
        return new AgentToolExecutionResult(
                objectMapper.valueToTree(note),
                "知识笔记创建完成，笔记ID=" + note.id()
        );
    }
}
