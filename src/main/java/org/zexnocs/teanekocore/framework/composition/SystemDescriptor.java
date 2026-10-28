package org.zexnocs.teanekocore.framework.composition;

import java.util.Objects;
import java.util.Set;

/**
 * System 的稳定标识、必需输入、允许写入及显式前置依赖。
 *
 * @param id 稳定标识
 * @param required 步骤执行所必需的组件键
 * @param writes 步骤允许写入的组件键
 * @param after 必须先完成的步骤标识集合
 * @param optional 缺少必需输入时是否允许跳过
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record SystemDescriptor(
        String id,
        Set<ComponentKey<?>> required,
        Set<ComponentKey<?>> writes,
        Set<String> after,
        boolean optional
) {
    /**
     * 校验并固定当前实例的初始数据；集合在构造时复制，避免外部修改影响本次状态。
     *
     * @param id 稳定标识
     * @param required 步骤执行所必需的组件键
     * @param writes 步骤允许写入的组件键
     * @param after 必须先完成的步骤标识集合
     * @param optional 缺少必需输入时是否允许跳过
     * @throws IllegalArgumentException 当参数格式、范围或声明的类型约束不满足时
     * @throws NullPointerException 当必需的输入或依赖为空时
     */
    public SystemDescriptor {
        id = Objects.requireNonNull(id, "id").strip();
        required = Set.copyOf(required);
        writes = Set.copyOf(writes);
        after = Set.copyOf(after);
        if (id.isEmpty() || after.stream().anyMatch(value -> value.isBlank() || !value.equals(value.strip()))) {
            throw new IllegalArgumentException("System ids must be nonblank and have no surrounding whitespace");
        }
    }

    /**
     * 固定步骤输入、写入和前置依赖声明，省略可选标志时默认要求必需输入存在。
     *
     * @param id 稳定标识
     * @param required 步骤执行所必需的组件键
     * @param writes 步骤允许写入的组件键
     * @param after 必须先完成的步骤标识集合
     */
    public SystemDescriptor(String id, Set<ComponentKey<?>> required, Set<ComponentKey<?>> writes, Set<String> after) {
        this(id, required, writes, after, false);
    }
}
