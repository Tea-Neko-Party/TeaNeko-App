package org.zexnocs.teanekoagent.llm_api_framework.tool;

import org.zexnocs.teanekoagent.llm_api_framework.tool.interfaces.ILLMFunctionParameter;

import java.util.List;

/**
 * 抽象 LLM Function Parameter，实现了基础的功能。
 *
 * @author zExNocs
 * @date 2026/03/29
 * @since 4.4.0
 */
public class AbstractLLMFunctionParameter implements ILLMFunctionParameter {
    /**
     * 类型，包含：
     * <br>object, string, number, integer, boolean, array, enum, anyOf
     */
    @Override
    public String getType() {
        return "";
    }

    /**
     * 对该参数的描述
     *
     * @return {@link String }
     */
    @Override
    public String getDescription() {
        return "";
    }

    /**
     * 必须需要的参数名称列表
     * object 中的 properties 所有属性必须设置在 required 中
     *
     * @return {@link List }<{@link String }>
     */
    @Override
    public List<String> getRequired() {
        return List.of();
    }
}
