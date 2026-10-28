package org.zexnocs.teanekoagent.agent;

import org.zexnocs.teanekocore.framework.composition.EntityId;
import org.zexnocs.teanekocore.framework.composition.EntitySnapshot;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 并发实例注册表，登记与关闭线性化，关闭回调始终在锁外执行。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public final class AgentRegistry implements IAgentRegistry {
    private final Map<EntityId, AgentHandle> instances = new ConcurrentHashMap<>();
    private final ReentrantLock lifecycle = new ReentrantLock();
    private volatile boolean closed;

    /**
     * 登记新实例，拒绝关闭状态和重复 ID，不授予业务执行权限。
     * @param handle 新实例
     * @throws IllegalStateException 当注册表或实例已关闭、ID 已存在时
     */
    @Override public void register(AgentHandle handle) {
        Objects.requireNonNull(handle, "Agent 实例不能为空。");
        lifecycle.lock();
        try {
            if (closed || handle.isClosed()) { throw new IllegalStateException("无法登记已关闭的 Agent 实例或注册表。"); }
            if (instances.putIfAbsent(handle.id(), handle) != null) { throw new IllegalStateException("Agent 实例 ID 已登记。"); }
        } finally { lifecycle.unlock(); }
    }

    /**
     * 查询当前实例，返回句柄后仍需在执行门控中复核有效性。
     * @param id 实例标识
     * @return 当前拥有的句柄
     */
    @Override public Optional<AgentHandle> find(EntityId id) {
        Objects.requireNonNull(id, "Agent 实例标识不能为空。");
        return closed ? Optional.empty() : Optional.ofNullable(instances.get(id));
    }

    /**
     * 先移除拥有关系再在锁外关闭，允许关闭监听器安全重入注册表。
     * @param id 目标实例标识
     * @return 找到并移除时为 true
     */
    @Override public boolean remove(EntityId id) {
        Objects.requireNonNull(id, "Agent 实例标识不能为空。");
        final AgentHandle handle;
        lifecycle.lock();
        try { handle = instances.remove(id); }
        finally { lifecycle.unlock(); }
        if (handle == null) { return false; }
        handle.close();
        return true;
    }

    /**
     * 复制当前数据快照，不在锁内执行用户回调。
     * @return 不可变列表
     */
    @Override public List<EntitySnapshot> snapshots() {
        return instances.values().stream().map(AgentHandle::snapshot).toList();
    }

    /** 关闭登记入口后一次性移交实例列表，逐个在锁外释放其生命周期。 */
    @Override public void close() {
        final List<AgentHandle> owned;
        lifecycle.lock();
        try {
            if (closed) { return; }
            closed = true;
            owned = List.copyOf(instances.values());
            instances.clear();
        } finally { lifecycle.unlock(); }
        owned.forEach(AgentHandle::close);
    }
}
