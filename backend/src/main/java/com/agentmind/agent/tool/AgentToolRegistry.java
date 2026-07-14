package com.agentmind.agent.tool;

import com.agentmind.common.exception.BusinessException;
import com.agentmind.common.exception.ErrorCode;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 智能体工具白名单注册表。
 *
 * <p>应用启动时由 Spring 收集全部 AgentTool 实现。重复工具名会使应用启动失败，
 * 防止两个模块在不知情的情况下覆盖同一个工具行为。</p>
 */
@Component
public class AgentToolRegistry {

    private final Map<String, AgentTool> toolsByName;

    public AgentToolRegistry(Collection<AgentTool> tools) {
        Map<String, AgentTool> registered = new LinkedHashMap<>();
        for (AgentTool tool : tools) {
            String normalizedName = normalize(tool.definition().name());
            AgentTool existing = registered.putIfAbsent(normalizedName, tool);
            if (existing != null) {
                throw new IllegalStateException("检测到重复的智能体工具名称：" + normalizedName);
            }
        }
        this.toolsByName = Map.copyOf(registered);
    }

    public AgentTool requireTool(String toolName) {
        AgentTool tool = toolsByName.get(normalize(toolName));
        if (tool == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请求的工具不在白名单中");
        }
        return tool;
    }

    /**
     * 返回注册表快照，供 Spring AI 适配器生成模型工具定义。
     *
     * <p>调用方只能读取快照，不能在运行期间修改白名单。</p>
     */
    public Collection<AgentTool> registeredTools() {
        return toolsByName.values();
    }

    private String normalize(String toolName) {
        return toolName == null ? "" : toolName.trim().toLowerCase(Locale.ROOT);
    }
}
