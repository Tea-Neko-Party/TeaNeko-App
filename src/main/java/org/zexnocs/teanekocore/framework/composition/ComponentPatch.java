package org.zexnocs.teanekocore.framework.composition;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 一次不可变组件变更。相同类型的键重复写入时以后一次为准。
 * null 不作为组件值；删除必须显式调用 remove。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public final class ComponentPatch {
    private static final ComponentPatch EMPTY = new ComponentPatch(Map.of());
    private final Map<String, Change> changes;

    /**
     * 固定本次组件变更集合，防止构建方在提交期间继续修改。
     *
     * @param changes 本次尚未提交的组件变更
     */
    private ComponentPatch(Map<String, Change> changes) {
        this.changes = Map.copyOf(changes);
    }

    /**
     * 复用无变更补丁，供只校验版本或不写入的步骤使用。
     *
     * @return 冻结当前变更集合的组件补丁
     */
    public static ComponentPatch empty() {
        return EMPTY;
    }

    /**
     * 创建独立补丁构建器，将修改收集与实体提交分离。
     *
     * @return 当前构建器，供调用方继续组合后提交
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 判断补丁是否包含任何添加、替换或显式删除。
     *
     * @return 本次未登记任何变更时为 true
     */
    public boolean isEmpty() {
        return changes.isEmpty();
    }

    /**
     * 返回本次变更涉及的类型化键，用于校验 System 的写入声明。
     *
     * @return 保持本方法顺序和筛选规则的结果集合
     */
    public Set<ComponentKey<?>> changedKeys() {
        return changes.values().stream().map(Change::key).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * 向同包提交逻辑提供不可变变更表，不暴露给业务调用方直接写入。
     *
     * @return 按当前定位规则生成的映射；是否可修改遵循本方法说明
     */
    Map<String, Change> changes() {
        return changes;
    }

    // null value 在内部表示删除，同时保留键的类型绑定。
    /**
     * 单项类型化组件变更；空值仅在内部表示显式删除。
     *
     * @param key 定位目标数据的键
     * @param value 待处理的值
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    record Change(ComponentKey<?> key, Object value) {}

    /**
     * 在提交前收集类型安全变更，构建出的补丁与后续修改独立。
     *
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    public static final class Builder {
        private final Map<String, Change> changes = new LinkedHashMap<>();

        /**
         * 创建尚未提交的组件变更缓冲，不共享其他构建器的状态。
         */
        private Builder() {}

        /**
         * 校验组件类型并记录非空值；相同类型的键重复写入时保留最后一次变更。
         *
         * @param key 定位目标数据的键
         * @param value 待处理的值
         * @param <T> 本方法约定的泛型数据类型
         * @return 当前构建器，供调用方继续组合后提交
         * @throws NullPointerException 当必需的输入或依赖为空时
         */
        public <T> Builder put(ComponentKey<T> key, T value) {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");
            key.type().cast(value);
            return change(new Change(key, value));
        }

        /**
         * 显式记录组件删除，并保留键的类型绑定供提交时校验。
         *
         * @param key 定位目标数据的键
         * @return 当前构建器，供调用方继续组合后提交
         * @throws NullPointerException 当必需的输入或依赖为空时
         */
        public Builder remove(ComponentKey<?> key) {
            return change(new Change(Objects.requireNonNull(key, "key"), null));
        }

        /**
         * 统一合并变更，拒绝把同一稳定键解释为不同组件类型。
         *
         * @param change 需要记录的单项组件变更
         * @return 当前构建器，供调用方继续组合后提交
         * @throws IllegalArgumentException 当参数格式、范围或声明的类型约束不满足时
         */
        private Builder change(Change change) {
            var previous = changes.get(change.key().id());
            if (previous != null && !previous.key().equals(change.key())) {
                throw new IllegalArgumentException("Conflicting component type: " + change.key().id());
            }
            changes.put(change.key().id(), change);
            return this;
        }

        /**
         * 冻结当前变更集合；空构建器复用无变更补丁，后续构建不修改已返回补丁。
         *
         * @return 冻结当前变更集合的组件补丁
         */
        public ComponentPatch build() {
            return changes.isEmpty() ? EMPTY : new ComponentPatch(changes);
        }
    }
}
