package org.zexnocs.teanekocore.framework.composition;

import java.util.ConcurrentModificationException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 实体拥有者通过版本化提交更新状态；管线运行期间同一实体只接受一个运行租约。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public final class Entity {
    private final AtomicReference<EntitySnapshot> state;
    private final AtomicBoolean executing = new AtomicBoolean();

    /**
     * 建立独立实体状态和执行租约；初始补丁在版本零上应用，组件值须由业务方保证不可变。
     *
     * @param id 稳定标识
     */
    public Entity(EntityId id) {
        this(id, ComponentPatch.empty());
    }

    /**
     * 建立独立实体状态和执行租约；初始补丁在版本零上应用，组件值须由业务方保证不可变。
     *
     * @param id 稳定标识
     * @param initial 创建时需要应用的初始组件补丁
     */
    public Entity(EntityId id, ComponentPatch initial) {
        state = new AtomicReference<>(EntitySnapshot.of(id, initial));
    }

    /**
     * 取得当前不可变组件快照，供步骤读取而不直接修改实体。
     *
     * @return 实体当前版本的只读组件快照
     */
    public EntitySnapshot snapshot() {
        return state.get();
    }

    /**
     * 在预期版本上原子提交补丁；版本过期或并发竞争失败时不产生部分写入。
     *
     * @param expectedRevision 提交方读取快照时记录的预期版本
     * @param patch 本步骤声明的不可变组件补丁
     * @return 提交成功后的不可变快照，空补丁仍校验预期版本
     * @throws NullPointerException 当必需的输入或依赖为空时
     */
    public EntitySnapshot commit(long expectedRevision, ComponentPatch patch) {
        Objects.requireNonNull(patch, "patch");
        var current = state.get();
        if (current.revision() != expectedRevision) {
            throw stale(expectedRevision, current.revision());
        }
        var updated = current.apply(patch);
        if (!state.compareAndSet(current, updated)) {
            throw stale(expectedRevision, state.get().revision());
        }
        return updated;
    }

    /**
     * 尝试取得本实体唯一的在途管线租约，不阻止其他实体并行执行。
     *
     * @return 取得本实体执行租约时为 true，已有在途管线时为 false
     */
    boolean acquireExecution() {
        return executing.compareAndSet(false, true);
    }

    /**
     * 释放本实体的管线租约，使后续请求可以开始执行。
     */
    void releaseExecution() {
        executing.set(false);
    }

    /**
     * 构造只包含实体标识和版本差异的并发异常，不携带组件业务内容。
     *
     * @param expected 约定的预期状态或版本
     * @param actual 当前观察到的状态或版本
     * @return 满足本方法约束的 {@code ConcurrentModificationException} 结果
     */
    private ConcurrentModificationException stale(long expected, long actual) {
        return new ConcurrentModificationException(
                "Entity " + state.get().id().value() + " revision changed: expected " + expected + ", actual " + actual);
    }
}
