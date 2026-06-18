package org.zexnocs.teanekoagent_old.tool;

import org.zexnocs.teanekoagent_old.llm.framework.tool.interfaces.ILLMTool;
import org.zexnocs.teanekoagent_old.llm.framework.tool.interfaces.ILLMToolService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TeaNeko Agent 工具提供者接口。
 * <br>外部模块可以将 Skills、MCP 或其他能力包装成 {@link ILLMTool} 后通过该接口注入，避免 Agent 层重复定义工具模型。
 *
 * @author zExNocs
 * @date 2026/06/09
 * @since 4.4.1
 */
public interface IAgentToolProvider {
    /**
     * 获取工具提供者 ID。
     *
     * @return 工具提供者 ID。
     */
    default String getProviderId() {
        return getClass().getName();
    }

    /**
     * 获取当前提供者暴露的全部 LLM 工具。
     *
     * @return 工具名称到 LLM 工具的映射。
     */
    Map<String, ILLMTool> getTools();

    /**
     * 按工具包获取当前提供者暴露的 LLM 工具。
     *
     * @param toolPackage 工具包名称。
     * @return 工具名称到 LLM 工具的映射。
     */
    default Map<String, ILLMTool> getTools(String toolPackage) {
        if (toolPackage == null || toolPackage.isBlank()) {
            return Map.of();
        }
        if (ILLMToolService.ALL_TOOL_PACKAGES.equalsIgnoreCase(toolPackage)) {
            return getTools();
        }
        var result = new LinkedHashMap<String, ILLMTool>();
        for (var entry : getTools().entrySet()) {
            if (toolPackage.equals(entry.getValue().getToolPackage())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return Map.copyOf(result);
    }
}
