package org.zexnocs.teanekoagent.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import com.fasterxml.jackson.annotation.JsonValue;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.framework.composition.ComponentPatch;
import org.zexnocs.teanekocore.framework.composition.Entity;
import org.zexnocs.teanekocore.framework.composition.EntityId;
import org.zexnocs.teanekocore.framework.composition.EntitySnapshot;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 独立 Agent 实例的受控拥有者，只导出实体快照；关闭信号与业务数据分开保存。
 * 不创建线程池、父调用预算或后台任务，也不在组件中放置运行控制。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
@JsonIgnoreType
public final class AgentHandle implements AutoCloseable {
    private final Entity entity;
    private final ExecutionControl lifecycle = ExecutionControl.create();
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * 为工厂创建独立实体，定义可以共享，实例状态不共享。
     * @param definition 已冻结定义
     */
    AgentHandle(AgentDefinition definition) {
        entity = new Entity(EntityId.random(), ComponentPatch.builder()
                .put(AgentComponents.DEFINITION, Objects.requireNonNull(definition, "Agent 定义不能为空。"))
                .put(AgentComponents.CLOSED, false).build());
    }

    /**
     * 读取实例稳定 ID，不编码用户或 scope 授权。
     * @return 当前实例标识
     */
    public EntityId id() { return entity.snapshot().id(); }

    /**
     * 读取可安全共享的定义，不能通过它修改实例状态。
     * @return 本实例定义
     */
    public AgentDefinition definition() { return entity.snapshot().require(AgentComponents.DEFINITION); }

    /**
     * 导出类型化只读数据，不导出实体写入口或生命周期控制。
     * @return 当前组件快照
     */
    public EntitySnapshot snapshot() { return entity.snapshot(); }

    /**
     * 检查是否已经开始关闭，供新活动快速拒绝。
     * @return 实例已关闭时为 true
     */
    public boolean isClosed() { return closed.get(); }

    /**
     * 订阅关闭信号；已经关闭时立即通知，调用方须解除完成任务的监听。
     * 回调在组件提交之后执行，不在注册表锁内执行。
     * @param listener 用于取消该实例当前活动的回调
     * @return 可释放的监听登记
     */
    public ExecutionControl.TerminationRegistration onClose(Runnable listener) {
        Objects.requireNonNull(listener, "关闭监听不能为空。");
        return lifecycle.onTermination(ignored -> listener.run());
    }

    /** 关闭当前实例并通知活动拥有者；幂等，不关闭其他实例或共享服务。 */
    @Override public void close() {
        if (closed.compareAndSet(false, true)) {
            entity.commit(entity.snapshot().revision(), ComponentPatch.builder().put(AgentComponents.CLOSED, true).build());
            lifecycle.close();
        }
    }

    /**
     * 仅输出实例 ID 和关闭状态，避免日志或 JSON 顺带展开组件和运行对象。
     * @return 实例摘要
     */
    @Override @JsonValue public String toString() { return "AgentHandle[id=" + id().value() + ", closed=" + isClosed() + "]"; }
}
