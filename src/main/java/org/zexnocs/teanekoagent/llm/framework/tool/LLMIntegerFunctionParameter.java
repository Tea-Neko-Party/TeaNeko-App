package org.zexnocs.teanekoagent.llm.framework.tool;

import java.util.List;
import java.util.Map;

/**
 * 大语言模型 Function Tool 的 integer 参数。
 *
 * @author zExNocs
 * @date 2026/05/15
 * @since 4.4.0
 */
public class LLMIntegerFunctionParameter extends AbstractLLMFunctionParameter {
    /**
     * 创建不带描述的 integer 参数。
     */
    public LLMIntegerFunctionParameter() {
        this(null);
    }

    /**
     * 创建 integer 参数。
     *
     * @param description 参数描述
     */
    public LLMIntegerFunctionParameter(String description) {
        super("integer", description, List.of(), Map.of(), null, List.of(), false);
    }
}
