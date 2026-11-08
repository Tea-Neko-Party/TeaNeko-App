package org.zexnocs.teanekoagent.llm.framework.tool.parameter;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import java.util.List;
import java.util.Map;

/**
 * 大语言模型 Function Tool 的 integer 参数。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：迁入独立 LLM 子模块，保留公开协议与原配置语义。
 *
 * @author zExNocs
 * @date 2026/05/15
 * @since 4.4.0
 * @version 4.9.0-l
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
