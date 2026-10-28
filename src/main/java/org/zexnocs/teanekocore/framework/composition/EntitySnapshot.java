package org.zexnocs.teanekocore.framework.composition;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 不可变实体快照。容器不会深拷贝业务值，调用方须使用不可变组件。
 * 已删除组件仍保留类型绑定，避免同一个稳定 id 被重新解释为另一种类型。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public final class EntitySnapshot implements ComponentView {
    private final EntityId id;
    private final long revision;
    private final Map<String, ComponentPatch.Change> components;

    /**
     * 复制组件映射并固定实体标识与版本；不深拷贝业务组件值。
     *
     * @param id 稳定标识
     * @param revision 快照版本
     * @param components 组件值与稳定类型绑定的映射
     * @throws NullPointerException 当必需的输入或依赖为空时
     */
    private EntitySnapshot(EntityId id, long revision, Map<String, ComponentPatch.Change> components) {
        this.id = Objects.requireNonNull(id, "id");
        this.revision = revision;
        this.components = Map.copyOf(components);
    }

    /**
     * 在版本零应用初始补丁，建立带稳定类型绑定的不可变实体快照。
     *
     * @param id 稳定标识
     * @param initial 创建时需要应用的初始组件补丁
     * @return 包含实体标识、版本和不可变组件映射的快照
     * @throws NullPointerException 当必需的输入或依赖为空时
     */
    public static EntitySnapshot of(EntityId id, ComponentPatch initial) {
        return new EntitySnapshot(id, 0, applyChanges(Map.of(), Objects.requireNonNull(initial, "initial")));
    }

    /**
     * 提供快照所属实体标识，供拥有者关联提交目标。
     *
     * @return 可用于新实体实例的稳定标识
     */
    public EntityId id() {
        return id;
    }

    /**
     * 提供本次快照版本，用于提交前的乐观并发校验。
     *
     * @return 本次快照对应的实体版本
     */
    public long revision() {
        return revision;
    }

    /**
     * 按键读取仍存在的组件并检查类型，已删除组件不返回值但保留其类型约束。
     *
     * @param key 定位目标数据的键
     * @param <T> 本方法约定的泛型数据类型
     * @return 匹配键类型的组件；未写入或已删除时返回空
     * @throws NullPointerException 当必需的输入或依赖为空时
     */
    @Override
    public <T> Optional<T> find(ComponentKey<T> key) {
        Objects.requireNonNull(key, "key");
        var entry = components.get(key.id());
        if (entry == null) {
            return Optional.empty();
        }
        requireSameType(entry.key(), key);
        return Optional.ofNullable(entry.value()).map(key.type()::cast);
    }

    /**
     * 返回实际有值的组件键，不将已删除组件的类型占位误报为能力存在。
     *
     * @return 当前实际存在且有值的组件键的不可变集合
     */
    @Override
    public Set<ComponentKey<?>> keys() {
        return components.values().stream()
                .filter(entry -> entry.value() != null)
                .map(ComponentPatch.Change::key)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * 基于当前快照创建下一版本，空补丁复用原快照，不修改既有快照。
     *
     * @param patch 本步骤声明的不可变组件补丁
     * @return 原快照或应用补丁后的下一版本快照
     */
    EntitySnapshot apply(ComponentPatch patch) {
        if (patch.isEmpty()) {
            return this;
        }
        return new EntitySnapshot(id, Math.incrementExact(revision), applyChanges(components, patch));
    }

    /**
     * 将添加、替换和删除合并到新映射，逐项检查已绑定类型，避免部分变更写入原状态。
     *
     * @param source 待处理的来源数据
     * @param patch 本步骤声明的不可变组件补丁
     * @return 按当前定位规则生成的映射；是否可修改遵循本方法说明
     */
    private static Map<String, ComponentPatch.Change> applyChanges(
            Map<String, ComponentPatch.Change> source, ComponentPatch patch) {
        var result = new HashMap<>(source);
        for (var entry : patch.changes().entrySet()) {
            var existing = result.get(entry.getKey());
            if (existing != null) {
                requireSameType(existing.key(), entry.getValue().key());
            }
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    /**
     * 确保同一稳定组件键始终绑定同一种类型，防止读取或删除后的类型混淆。
     *
     * @param existing 已经固定的组件类型绑定
     * @param requested 本次申请额度
     * @throws IllegalArgumentException 当参数格式、范围或声明的类型约束不满足时
     */
    private static void requireSameType(ComponentKey<?> existing, ComponentKey<?> requested) {
        if (!existing.equals(requested)) {
            throw new IllegalArgumentException("Conflicting component type: " + requested.id());
        }
    }
}
