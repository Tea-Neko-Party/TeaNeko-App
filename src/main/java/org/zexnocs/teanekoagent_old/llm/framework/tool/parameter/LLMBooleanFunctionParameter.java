package org.zexnocs.teanekoagent_old.llm.framework.tool.parameter;

import java.util.List;
import java.util.Map;

/**
 * 大语言模型 Function Tool 的 boolean 参数。
 *
 * @author zExNocs
 * @date 2026/05/15
 * @since 4.4.0
 */
public class LLMBooleanFunctionParameter extends AbstractLLMFunctionParameter {
    /**
     * 创建不带描述的 boolean 参数。
     */
    public LLMBooleanFunctionParameter() {
        this(null);
    }

    /**
     * 创建 boolean 参数。
     *
     * @param description 参数描述
     */
    public LLMBooleanFunctionParameter(String description) {
        super("boolean", description, List.of(), Map.of(), null, List.of(), false);
    }
}
