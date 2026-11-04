package org.zexnocs.teanekoagent.component.memory;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.component.AgentComponents;
import org.zexnocs.teanekoagent.component.memory.history.IAgentHistoryRecorder;
import org.zexnocs.teanekocore.framework.composition.*;

import java.util.Set;
import java.util.concurrent.CompletionStage;

/**
 * 在模型交互前提交原用户历史，只由记忆组件安装，领域服务继续复核采集权限与版本。
 *
 * <br>2026/11/04：更新分层重构后的类型引用，保持原调用与生命周期行为。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.8.0-l
 * @version 4.8.0-l
 */
@Component
public final class MemoryInputSystem implements IEntitySystem {
    private final ObjectProvider<IAgentHistoryRecorder> history;

    /** @param history 原持久历史服务 */
    public MemoryInputSystem(ObjectProvider<IAgentHistoryRecorder> history) {
        this.history = history;
    }

    /** @return 仅观察原请求与布局、不修改宿主数据的系统声明 */
    @Override
    public SystemDescriptor descriptor() {
        return new SystemDescriptor("agent.component.memory.capture", Set.of(AgentComponents.REQUEST,
                AgentComponents.PROMPT_INPUT, AgentComponents.MEMORY_REFERENCE), Set.of(), Set.of());
    }

    /**
     * @param context 已准入的固定上下文
     * @return 实际数据库提交完成后继续
     */
    @Override
    public CompletionStage<SystemResult> execute(SystemContext context) {
        var snapshot = context.snapshot();
        var request = snapshot.require(AgentComponents.REQUEST)
                .withMemoryReference(snapshot.require(AgentComponents.MEMORY_REFERENCE));
        return history.getObject().record(request, snapshot.require(AgentComponents.PROMPT_INPUT), null, context.control())
                .thenApply(ignored -> SystemResult.continueWith(ComponentPatch.empty()));
    }
}
