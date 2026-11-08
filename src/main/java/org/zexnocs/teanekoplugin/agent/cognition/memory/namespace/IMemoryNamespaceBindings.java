package org.zexnocs.teanekoplugin.agent.cognition.memory.namespace;

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

import org.zexnocs.teanekoagent.runtime.AgentActorContext;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * MemoryNamespaceBindings 的类型化业务端口；实现由所属插件提供，底层不依赖插件实现。
 * <br>2026/11/07：分离声明与实现，保留原真实完成和权限约束。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
public interface IMemoryNamespaceBindings {
    /**
     * 读取当前权威引用，不创建默认空间；配置及权限判断仍由上层负责。
     * @param scope 真实作用域
     * @return 当前已登记空间的事务引用观察
     */
    CompletionStage<Optional<MemoryNamespaceReference>> readReference(AgentScope scope);
    /**
     * 用同一事务创建新空间、占用配额并修改真实发起 scope 的绑定。
     * @param requestId 原始请求 ID
     * @param actor 已经上层认证授权的操作者
     * @param expectedRevision 原始预期绑定版本，重试保留
     * @return 已提交的操作回执；尚未同步会话，不直接作为平台已切换反馈
     */
    CompletionStage<MemoryNamespaceBindingOutcome> createAndBind(UUID requestId, AgentActorContext actor, long expectedRevision);
    /**
     * 绑定已有空间，不修改原创建来源，不要求目标属于当前操作者，也不读取创建配额。
     * @param requestId 原始操作 ID
     * @param actor 经上层认证授权的操作者
     * @param target 允许绑定的已有业务空间
     * @param expectedRevision 原始预期版本
     * @return 持久化后的绑定或明确拒绝回执
     */
    CompletionStage<MemoryNamespaceBindingOutcome> bindExisting(UUID requestId, AgentActorContext actor,
            MemoryNamespaceId target, long expectedRevision);
}
