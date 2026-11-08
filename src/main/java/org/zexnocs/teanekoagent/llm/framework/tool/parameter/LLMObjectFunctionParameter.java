package org.zexnocs.teanekoagent.llm.framework.tool.parameter;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoagent.llm.framework.tool.interfaces.ILLMFunctionParameter;

import java.util.List;
import java.util.Map;

/**
 * 大语言模型 Function Tool 的 object 参数。
 * <br>用于组合多个子参数并声明 required 字段。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：迁入独立 LLM 子模块，保留公开协议与原配置语义。
 *
 * @author zExNocs
 * @date 2026/05/15
 * @since 4.4.0
 * @version 4.9.0-l
 */
public class LLMObjectFunctionParameter extends AbstractLLMFunctionParameter {
    /**
     * 创建不允许额外属性的 object 参数。
     *
     * @param description 对象参数描述
     * @param properties 对象属性定义
     * @param required 必填属性名称列表
     */
    public LLMObjectFunctionParameter(String description,
                                      Map<String, ILLMFunctionParameter> properties,
                                      List<String> required) {
        this(description, properties, required, false);
    }

    /**
     * 创建 object 参数。
     *
     * @param description 对象参数描述
     * @param properties 对象属性定义
     * @param required 必填属性名称列表
     * @param additionalProperties 是否允许未声明的额外属性
     */
    public LLMObjectFunctionParameter(String description,
                                      Map<String, ILLMFunctionParameter> properties,
                                      List<String> required,
                                      boolean additionalProperties) {
        super("object", description, required, properties, null, List.of(), additionalProperties);
    }
}
