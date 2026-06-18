package org.zexnocs.teanekoagent_old.llm.framework.tool.interfaces;

/**
 * 大语言模型工具执行器接口。
 * <br>接收模型生成的参数字符串，并返回工具执行结果。
 *
 * @author zExNocs
 * @date 2026/05/15
 * @since 4.4.0
 */
@FunctionalInterface
public interface ILLMToolExecutor {
    /**
     * 执行工具调用。
     * <br>{@code arguments} 通常是模型生成的 JSON 字符串，具体解析方式由工具实现自行决定。
     *
     * @param arguments 模型生成的工具参数字符串
     * @return 工具执行结果，通常会作为 tool message 返回给模型
     * @throws Exception 工具执行失败时抛出，交由调用方统一处理
     */
    String call(String arguments) throws Exception;
}
