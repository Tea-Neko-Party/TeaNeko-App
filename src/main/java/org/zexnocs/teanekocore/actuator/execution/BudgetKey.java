package org.zexnocs.teanekocore.actuator.execution;

import java.util.Objects;

/**
 * 通用资源维度；模型、工具等具体维度由使用方声明。
 *
 * @param value 待处理的值
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record BudgetKey(String value) {
    /**
     * 规范化资源维度名称，拒绝空标识，避免隐式创建无约束的预算维度。
     *
     * @param value 待处理的值
     * @throws IllegalArgumentException 当参数格式、范围或声明的类型约束不满足时
     * @throws NullPointerException 当必需的输入或依赖为空时
     */
    public BudgetKey {
        value = Objects.requireNonNull(value, "value").strip();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Budget key must not be blank");
        }
    }
}
