package org.zexnocs.teanekoagent_old.tool;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.llm.framework.tool.interfaces.ILLMTool;
import org.zexnocs.teanekoagent.llm.framework.tool.interfaces.ILLMToolCall;
import org.zexnocs.teanekoagent.llm.framework.tool.interfaces.ILLMToolService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TeaNeko Agent 工具注册表服务。
 * <br>该服务负责把内部 LLM Function Tool 与外部注入的 Skills、MCP 工具合并为统一的 {@link ILLMTool} 视图。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
@Service
public class AgentToolRegistryService {
    /**
     * 现有 LLM framework 工具服务。
     */
    private final ILLMToolService llmToolService;

    /**
     * 外部 Agent 工具提供者集合。
     */
    private final List<IAgentToolProvider> toolProviders;

    /**
     * 创建 TeaNeko Agent 工具注册表服务。
     *
     * @param llmToolService 现有 LLM framework 工具服务。
     * @param toolProviders  外部 Agent 工具提供者集合。
     */
    public AgentToolRegistryService(ILLMToolService llmToolService, List<IAgentToolProvider> toolProviders) {
        this.llmToolService = llmToolService;
        this.toolProviders = toolProviders == null ? List.of() : List.copyOf(toolProviders);
    }

    /**
     * 获取 Agent 当前可见的全部 LLM 工具。
     *
     * @return 工具名称到 LLM 工具的映射。
     */
    public Map<String, ILLMTool> getTools() {
        var result = new LinkedHashMap<String, ILLMTool>();
        mergeTools(result, llmToolService.getTools());
        mergeProviderTools(result, null);
        return Map.copyOf(result);
    }

    /**
     * 按工具包获取 Agent 当前可见的 LLM 工具。
     *
     * @param toolPackage 工具包名称，{@code all} 表示全部工具。
     * @return 工具名称到 LLM 工具的映射。
     */
    public Map<String, ILLMTool> getTools(String toolPackage) {
        if (toolPackage == null || toolPackage.isBlank()) {
            return Map.of();
        }
        if (ILLMToolService.ALL_TOOL_PACKAGES.equalsIgnoreCase(toolPackage)) {
            return getTools();
        }
        var result = new LinkedHashMap<String, ILLMTool>();
        mergeTools(result, llmToolService.getTools(toolPackage));
        mergeProviderTools(result, toolPackage);
        return Map.copyOf(result);
    }

    /**
     * 按工具包获取 Agent 当前可见的 LLM 工具列表。
     *
     * @param toolPackage 工具包名称。
     * @return LLM 工具列表。
     */
    public List<ILLMTool> getToolList(String toolPackage) {
        return List.copyOf(getTools(toolPackage).values());
    }

    /**
     * 判断工具是否已经注册到 Agent 工具视图中。
     *
     * @param toolName 工具名称。
     * @return 如果已经注册则返回 {@code true}。
     */
    public boolean hasTool(String toolName) {
        return getTools().containsKey(toolName);
    }

    /**
     * 根据工具名称获取 LLM 工具。
     *
     * @param toolName 工具名称。
     * @return LLM 工具。
     * @throws IllegalArgumentException 当工具不存在时抛出。
     */
    public ILLMTool getTool(String toolName) {
        var tool = getTools().get(toolName);
        if (tool == null) {
            throw new IllegalArgumentException("Agent tool is not registered: " + toolName);
        }
        return tool;
    }

    /**
     * 执行模型请求的工具调用。
     * <br>该方法通过 Agent 合并后的工具视图查找工具，因此同时支持 LLM framework 内部工具和外部 Agent 工具提供者。
     *
     * @param toolCall 模型请求执行的工具调用。
     * @return 工具执行结果。
     * @throws Exception 当工具不存在、参数解析失败或工具执行失败时抛出。
     */
    public String call(ILLMToolCall toolCall) throws Exception {
        if (toolCall == null) {
            throw new IllegalArgumentException("Agent tool call must not be null");
        }
        return getTool(toolCall.getName()).call(toolCall.getArguments());
    }

    /**
     * 合并外部 Agent 工具提供者的 LLM 工具。
     *
     * @param target      目标工具映射。
     * @param toolPackage 工具包名称，为 {@code null} 时合并全部工具。
     */
    private void mergeProviderTools(Map<String, ILLMTool> target, String toolPackage) {
        for (var provider : toolProviders) {
            var tools = toolPackage == null ? provider.getTools() : provider.getTools(toolPackage);
            mergeTools(target, tools);
        }
    }

    /**
     * 合并 LLM 工具映射。
     *
     * @param target 目标工具映射。
     * @param tools  LLM 工具映射。
     */
    private void mergeTools(Map<String, ILLMTool> target, Map<String, ILLMTool> tools) {
        for (var tool : tools.values()) {
            putIfAbsent(target, tool);
        }
    }

    /**
     * 当工具名称非空且目标映射中不存在同名工具时写入工具。
     *
     * @param target 目标工具映射。
     * @param tool   LLM 工具。
     */
    private void putIfAbsent(Map<String, ILLMTool> target, ILLMTool tool) {
        if (tool == null || tool.getName() == null || tool.getName().isBlank()) {
            return;
        }
        target.putIfAbsent(tool.getName(), tool);
    }
}
