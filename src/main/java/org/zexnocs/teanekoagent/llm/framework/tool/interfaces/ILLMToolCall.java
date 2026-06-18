package org.zexnocs.teanekoagent.llm.framework.tool.interfaces;

/**
 * 大语言模型工具调用接口。
 * <br>用于描述模型返回的 function call 请求。
 *
 * @author zExNocs
 * @date 2026/05/15
 * @since 4.4.0
 */
public interface ILLMToolCall {
    /**
     * 获取工具调用 ID。
     * <br>该 ID 通常由模型响应生成，用于在回传工具执行结果时关联原始调用。
     *
     * @return 工具调用 ID
     */
    String getId();

    /**
     * 获取工具调用类型。
     * <br>当前框架主要支持 Function Tool，因此默认返回 {@code function}。
     *
     * @return 工具调用类型
     */
    default String getType() {
        return "function";
    }

    /**
     * 获取被调用的工具名称。
     * <br>该名称应与已注册的 {@link ILLMTool#getName()} 保持一致。
     *
     * @return 工具名称
     */
    String getName();

    /**
     * 获取模型生成的工具调用参数。
     * <br>参数通常为 JSON 字符串，由具体工具执行器负责解析和校验。
     *
     * @return 工具调用参数
     */
    String getArguments();
}
