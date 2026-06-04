package org.zexnocs.teanekoagent.llm_api_framework.tool.interfaces;

import java.util.List;
import java.util.Map;

/**
 * 大语言模型工具服务接口。
 * <br>用于查询已注册工具并执行模型发起的 tool call。
 *
 * @author zExNocs
 * @date 2026/05/15
 * @since 4.4.0
 */
public interface ILLMToolService {
    /**
     * 表示选择所有已注册工具包的特殊值。
     */
    String ALL_TOOL_PACKAGES = "all";

    /**
     * 获取所有已注册的大语言模型工具。
     *
     * @return 工具名称到工具定义的映射
     */
    Map<String, ILLMTool> getTools();

    /**
     * 按工具包获取需要暴露给模型的大语言模型工具。
     * <br>{@code all} 表示返回所有已注册工具；{@code null} 或空字符串表示不暴露任何工具。
     *
     * @param toolPackage 工具包名称
     * @return 工具名称到工具定义的映射
     */
    Map<String, ILLMTool> getTools(String toolPackage);

    /**
     * 按工具包获取需要暴露给模型的大语言模型工具列表。
     *
     * @param toolPackage 工具包名称
     * @return 工具定义列表
     */
    default List<ILLMTool> getToolList(String toolPackage) {
        return List.copyOf(getTools(toolPackage).values());
    }

    /**
     * 根据工具名称获取工具定义。
     *
     * @param name 工具名称
     * @return 对应的工具定义
     * @throws IllegalArgumentException 当工具未注册时抛出
     */
    ILLMTool getTool(String name);

    /**
     * 执行模型发起的工具调用。
     *
     * @param toolCall 模型返回的工具调用请求
     * @return 工具执行结果，通常会作为 tool message 回传给模型
     * @throws Exception 当工具不存在、参数解析失败或工具执行失败时抛出
     */
    String call(ILLMToolCall toolCall) throws Exception;
}
