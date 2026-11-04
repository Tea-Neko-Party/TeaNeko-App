package org.zexnocs.teanekoagent.component.llm.framework.tool.parameter;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import java.util.List;
import java.util.Map;

/**
 * 大语言模型 Function Tool 的 boolean 参数。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/05/15
 * @since 4.4.0
 * @version 4.8.0-l
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
