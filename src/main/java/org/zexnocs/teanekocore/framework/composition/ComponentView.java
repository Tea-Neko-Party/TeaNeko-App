package org.zexnocs.teanekocore.framework.composition;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

/**
 * 只读组件视图，不提供服务定位或未检查的字符串取值接口。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public interface ComponentView {
    /**
     * 按稳定键和声明类型查找组件；缺失返回空，类型冲突属于契约错误。
     *
     * @param key 定位目标数据的键
     * @param <T> 本方法约定的泛型数据类型
     * @return 符合当前定位与类型约束的值；未配置或不存在时返回空
     */
    <T> Optional<T> find(ComponentKey<T> key);

    /**
     * 返回当前实际存在的组件键集合，不暴露底层可变存储。
     *
     * @return 保持本方法顺序和筛选规则的结果集合
     */
    Set<ComponentKey<?>> keys();

    /**
     * 读取必需组件；缺失时明确报告键，避免把装配错误解释为能力降级。
     *
     * @param key 定位目标数据的键
     * @param <T> 本方法约定的泛型数据类型
     * @return 满足本方法约束的 {@code T} 结果
     * @throws NoSuchElementException 当必需的类型化组件不存在时
     * @throws IllegalArgumentException 当参数格式、范围或声明的类型约束不满足时
     */
    default <T> T require(ComponentKey<T> key) {
        return find(key).orElseThrow(() -> new NoSuchElementException("Missing component: " + key.id()));
    }

    /**
     * 按类型化键判断组件是否存在，沿用类型冲突的校验规则。
     *
     * @param key 定位目标数据的键
     * @return 存在匹配类型的组件时为 true
     */
    default boolean contains(ComponentKey<?> key) {
        return find(key).isPresent();
    }
}
