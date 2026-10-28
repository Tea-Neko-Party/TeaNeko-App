package org.zexnocs.teanekocore.framework.composition;

import java.util.Objects;

/**
 * 稳定标识与运行时类型组成的组件键；组件值应当不可变。
 *
 * @param id 稳定标识
 * @param type 运行时类型
 * @param <T> 组件值类型
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record ComponentKey<T>(String id, Class<T> type) {
    /**
     * 固定稳定键与非基本运行时类型，拒绝空键，供组件容器校验同名异型冲突。
     *
     * @param id 稳定标识
     * @param type 运行时类型
     * @throws IllegalArgumentException 当参数格式、范围或声明的类型约束不满足时
     * @throws NullPointerException 当必需的输入或依赖为空时
     */
    public ComponentKey {
        id = Objects.requireNonNull(id, "id").strip();
        Objects.requireNonNull(type, "type");
        if (id.isEmpty() || type.isPrimitive()) {
            throw new IllegalArgumentException("Component key requires a nonblank id and a reference type");
        }
    }

    /**
     * 以稳定名称和运行时类型创建组件键，避免未检查的字符串强转。
     *
     * @param id 稳定标识
     * @param type 运行时类型
     * @param <T> 本方法约定的泛型数据类型
     * @return 以稳定名称和运行时类型绑定的组件键
     */
    public static <T> ComponentKey<T> of(String id, Class<T> type) {
        return new ComponentKey<>(id, type);
    }
}
