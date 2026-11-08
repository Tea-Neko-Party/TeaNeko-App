package org.zexnocs.teanekoagent.llm.framework.tool.parameter;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import java.util.List;
import java.util.Map;

/**
 * 大语言模型 Function Tool 的字符串参数。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：迁入独立 LLM 子模块，保留公开协议与原配置语义。
 *
 * @author zExNocs
 * @date 2026/03/30
 * @since 4.4.0
 * @version 4.9.0-l
 */
public class LLMStringFunctionParameter extends AbstractLLMFunctionParameter {
    /**
     * 创建不带描述的字符串参数。
     */
    public LLMStringFunctionParameter() {
        this(null);
    }

    /**
     * 创建字符串参数。
     *
     * @param description 参数描述
     */
    public LLMStringFunctionParameter(String description) {
        super("string", description, List.of(), Map.of(), null, List.of(), false);
    }

    /**
     * 创建带枚举约束的字符串参数。
     *
     * @param description 参数描述
     * @param enumValues 字符串允许的枚举值列表
     */
    public LLMStringFunctionParameter(String description, List<String> enumValues) {
        super("string", description, List.of(), Map.of(), null, enumValues, false);
    }
}
