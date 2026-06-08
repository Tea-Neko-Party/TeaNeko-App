package org.zexnocs.teanekoagent.llm.framework.tool.interfaces;

import org.zexnocs.teanekocore.framework.function.MethodCallable;

/**
 * 大语言模型 Function Tool 接口。
 * <br>用于向不同供应商暴露统一的工具定义和执行入口。
 *
 * @author zExNocs
 * @date 2026/03/24
 * @since 4.4.0
 */
public interface ILLMTool extends MethodCallable<String> {
    /**
     * 类型，一般都为 function
     *
     * @return {@link String }
     */
    default String getType() {
        return "function";
    }

    /**
     * tool 的名字
     *
     * @return {@link String }
     */
    String getName();

    /**
     * tool 的描述
     *
     * @return {@link String }
     */
    String getDescription();

    /**
     * 获取输入参数
     *
     * @return {@link ILLMFunctionParameter }
     */
    ILLMFunctionParameter getParameters();

    /**
     * 如果设置为 true，API 确保输出始终符合函数的 JSON schema 定义
     *
     * @return boolean
     */
    boolean isStrict();

    /**
     * 获取工具所属包。
     * <br>该字段用于按包筛选需要暴露给模型的工具，不参与供应商 Function Tool Schema。
     *
     * @return 工具所属包
     */
    default String getToolPackage() {
        return "";
    }

    /**
     * 大模型给出的参数值。只在大模型需求时才提供。
     *
     * @return {@link String }
     */
    default String getArguments() {
        return null;
    }

    /**
     * 调用工具执行逻辑，返回结果。参数为大模型给出的参数值。只在大模型需求时才提供。
     *
     * @param arguments 参数
     * @return {@link String }
     * @throws Exception 可能的异常
     */
    default String call(String arguments) throws Exception {
        return call();
    }
}
