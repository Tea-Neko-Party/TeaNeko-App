package org.zexnocs.teanekoagent.llm_api_framework.tool.interfaces;

import org.zexnocs.teanekocore.framework.function.MethodCallable;

/**
 * LLM 工具类接口
 *
 * @author zExNocs
 * @date 2026/03/24
 * @since 4.4.0
 */
public interface ILLMTool extends MethodCallable<String> {
    /**
     * tool 的名字。
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
     * 大模型给出的参数值。只在大模型需求时才提供。
     *
     * @return {@link String }
     */
    String getArguments();
}
