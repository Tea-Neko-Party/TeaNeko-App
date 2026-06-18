package org.zexnocs.teanekoagent.llm.framework.tool.parameter;

import org.zexnocs.teanekoagent.llm.framework.tool.interfaces.ILLMFunctionParameter;

import java.util.List;
import java.util.Map;

/**
 * 大语言模型 Function Tool 的 array 参数。
 *
 * @author zExNocs
 * @date 2026/05/15
 * @since 4.4.0
 */
public class LLMArrayFunctionParameter extends AbstractLLMFunctionParameter {
    /**
     * 创建 array 参数。
     *
     * @param description 数组参数描述
     * @param items 数组元素定义
     */
    public LLMArrayFunctionParameter(String description, ILLMFunctionParameter items) {
        super("array", description, List.of(), Map.of(), items, List.of(), false);
    }
}
