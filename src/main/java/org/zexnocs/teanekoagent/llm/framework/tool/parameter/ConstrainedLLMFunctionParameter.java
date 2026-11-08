package org.zexnocs.teanekoagent.llm.framework.tool.parameter;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

import org.zexnocs.teanekoagent.llm.framework.tool.interfaces.ILLMFunctionParameter;

import java.util.HashSet;
import java.util.List;

/**
 * 为已有类型描述附加静态约束，保留原工具与输出类型的共同描述入口。
 * <br>2026/11/06：不修改原类型描述或现有参数构造合同。
 * <br>2026/11/07：迁入独立 LLM 子模块，保留公开协议与原配置语义。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.6-l
 * @version 4.9.0-l
 */
public final class ConstrainedLLMFunctionParameter extends AbstractLLMFunctionParameter {
    private final LLMParameterLimits limits;

    /**
     * 复制已有声明并收紧枚举值，约束不扩大原枚举集合。
     * @param source 原类型描述
     * @param limits 固定容量约束
     * @param values 显式枚举，空表示保留原类型枚举
     * @throws IllegalArgumentException 当约束不适用于类型或扩大已有枚举时
     */
    public ConstrainedLLMFunctionParameter(ILLMFunctionParameter source, LLMParameterLimits limits, List<String> values) {
        super(source.getType(), source.getDescription(), source.getRequired(), source.getProperties(), source.getItems(),
                values.isEmpty() ? source.getEnumValues() : List.copyOf(values), source.isAdditionalProperties());
        limits.validateType(source.getType());
        if (!values.isEmpty() && (!"string".equals(source.getType())
                || !source.getEnumValues().isEmpty() && !new HashSet<>(source.getEnumValues()).containsAll(values)
                || values.stream().distinct().count() != values.size())) {
            throw new IllegalArgumentException("JSON 字段枚举声明无效。");
        }
        this.limits = limits;
    }

    /** @return 初始化时固定的容量合同 */
    @Override
    public LLMParameterLimits getLimits() {
        return limits;
    }
}
