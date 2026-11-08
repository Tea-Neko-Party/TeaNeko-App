package org.zexnocs.teanekoplugin.agent.cognition.memory.runtime;

// 2026/11/07：通过中立生命周期端口接入宿主，认知类型和业务校验仍归插件。
import org.zexnocs.teanekoagent.runtime.context.IAgentContextReferences;
import org.zexnocs.teanekoagent.runtime.context.IAgentContextReference;
import org.zexnocs.teanekoagent.component.AgentAssembly;

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoplugin.agent.cognition.memory.namespace.MemoryNamespaceReference;
import org.zexnocs.teanekoagent.runtime.AgentScope;

import java.util.concurrent.CompletionStage;

/**
 * 为独立轮次提供当前持久化空间引用及异步复核，不把存储资源或权限对象交给 LLM。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：由实现提供无存储副作用的当前资格，基础入口不识别插件或组件固定标识。
 *
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
public interface IAgentMemoryReferences extends IAgentContextReferences {
    /**
     * 查询当前作用域是否选择且仍可使用此引用能力，不读取或创建记忆存储。
     * 可选调用方可在不可用时继续无记忆请求；已固定的引用仍须调用 verify，不能降级为新请求。
     * @param scope 真实作用域
     * @return 当前组件、插件及作用域均允许使用此端口时为真
     */
    boolean available(AgentScope scope);

    /**
     * 在已通过 Agent 门控后解析当前引用，必要时按需登记默认共享空间。
     * @param scope 真实作用域
     * @return 已固定且定位一致的引用；未启用或存储不可用时异常结束
     */
    CompletionStage<MemoryNamespaceReference> resolve(AgentScope scope);

    /**
     * 只读复核原始引用，缺失或版本变化时失败，不创建空间或刷新本轮绑定。
     * @param expected 已固定的原始引用
     * @return 当前引用仍一致的完成信号，失败不得继续模型、工具或结果交付
     */
    CompletionStage<Void> verify(MemoryNamespaceReference expected);

    /** @param assembly 实际装配 @return 记忆组件是否参与本轮 */
    @Override
    default boolean supports(AgentAssembly assembly) {
        return assembly.has("memory");
    }

    /**
     * 将宿主固定引用交回认知业务复核，不接受其他提供者的引用。
     * @param expected 宿主固定引用
     * @return 原空间引用仍有效时完成
     */
    @Override
    default CompletionStage<Void> verify(IAgentContextReference expected) {
        if (!(expected instanceof MemoryNamespaceReference reference)) {
            throw new IllegalArgumentException("认知引用类型不匹配。");
        }
        return verify(reference);
    }

}
