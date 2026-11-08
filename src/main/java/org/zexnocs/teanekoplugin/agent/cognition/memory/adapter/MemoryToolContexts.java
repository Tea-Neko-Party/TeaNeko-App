package org.zexnocs.teanekoplugin.agent.cognition.memory.adapter;

import org.zexnocs.teanekoplugin.agent.cognition.CognitionContext;
// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoagent.component.AgentComponents;
import org.zexnocs.teanekoagent.llm.framework.tool.ToolInvocationContext;
import org.zexnocs.teanekoplugin.agent.cognition.memory.access.MemoryAccessContext;
import org.zexnocs.teanekoplugin.agent.cognition.memory.access.MemoryAccessException;

import java.util.Objects;

/**
 * 从宿主工具组件提取记忆访问上下文，不读取工具 JSON、Prompt metadata 或模型身份声明。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：组件数据引用迁入 component，保持原业务流程。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 *
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
@org.zexnocs.teanekocore.plugin.PluginOwned("agent.cognition")
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
            var reference = CognitionContext.reference(context.bindings().require(AgentComponents.CONTEXT_REFERENCE));
            if (!context.requestId().equals(request.requestId()) || !actor.equals(request.actor())
                    || CognitionContext.reference(request.memoryReference()) != null && !Objects.equals(CognitionContext.reference(request.memoryReference()), reference)) {
                throw new MemoryAccessException(MemoryAccessException.Reason.UNAVAILABLE);
            }
            return new MemoryAccessContext(request.requestId(), actor, reference);
        } catch (RuntimeException ignored) { throw new MemoryAccessException(MemoryAccessException.Reason.UNAVAILABLE); }
    }
}
