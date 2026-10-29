package org.zexnocs.teanekoagent.adapter.memory;

import org.zexnocs.teanekoagent.agent.AgentComponents;
import org.zexnocs.teanekoagent.llm.framework.tool.ToolInvocationContext;
import org.zexnocs.teanekoagent.memory.access.MemoryAccessContext;
import org.zexnocs.teanekoagent.memory.access.MemoryAccessException;

/**
 * 从宿主工具组件提取记忆访问上下文，不读取工具 JSON、Prompt metadata 或模型身份声明。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public final class MemoryToolContexts {
    /** 禁止实例化无状态工具上下文适配器。 */
    private MemoryToolContexts() {}

    /**
     * 同时校验请求和 actor 组件；缺失引用或交接不一致时拒绝，不临时解析另一个空间。
     * @param context 宿主提供的可信工具上下文
     * @return 不含运行资源的固定身份
     * @throws MemoryAccessException 当必需组件缺失或请求身份不一致时
     */
    public static MemoryAccessContext from(ToolInvocationContext context) {
        try {
            var request = context.bindings().require(AgentComponents.REQUEST);
            var actor = context.bindings().require(AgentComponents.ACTOR);
            var reference = context.bindings().require(AgentComponents.MEMORY_REFERENCE);
            if (!context.requestId().equals(request.requestId()) || !actor.equals(request.actor())
                    || request.memoryReference() != null && !request.memoryReference().equals(reference)) {
                throw new MemoryAccessException(MemoryAccessException.Reason.UNAVAILABLE);
            }
            return new MemoryAccessContext(request.requestId(), actor, reference);
        } catch (RuntimeException ignored) { throw new MemoryAccessException(MemoryAccessException.Reason.UNAVAILABLE); }
    }
}
