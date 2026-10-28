package org.zexnocs.teanekoagent.agent;

import org.zexnocs.teanekocore.framework.composition.EntityId;
import org.zexnocs.teanekocore.framework.composition.EntitySnapshot;

import java.util.List;
import java.util.Optional;

/**
 * Agent 实例拥有关系的边界，不负责 scope 注册、权限或持久化。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public interface IAgentRegistry extends AutoCloseable {
    /**
     * 登记唯一实例，不对已有实例进行隐式替换。
     * @param handle 新实例
     * @throws IllegalStateException 当注册表已关闭或 ID 已存在时
     */
    void register(AgentHandle handle);

    /**
     * 按 ID 查找当前拥有的实例。
     * @param id 实例标识
     * @return 已登记实例，未找到或注册表关闭时为空
     */
    Optional<AgentHandle> find(EntityId id);

    /**
     * 移除实例并在注册表锁外关闭，通知其在途活动。
     * @param id 待移除实例
     * @return 存在并移除时为 true
     */
    boolean remove(EntityId id);

    /**
     * 提供数据快照集合，不导出可变注册表容器。
     * @return 当前实例只读快照列表，不承诺跨实例事务快照
     */
    List<EntitySnapshot> snapshots();

    /** 关闭注册表及其拥有的全部实例，不关闭外部执行器。 */
    @Override void close();
}
