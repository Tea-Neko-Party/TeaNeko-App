package org.zexnocs.teanekoplugin.agent.cognition.memory.namespace;

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

import org.zexnocs.teanekoagent.runtime.AgentActorContext;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * MemoryNamespaceRegistry 的类型化业务端口；实现由所属插件提供，底层不依赖插件实现。
 * <br>2026/11/07：分离声明与实现，保留原真实完成和权限约束。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
public interface IMemoryNamespaceRegistry {
    /**
     * 显式登记默认共享空间，不占用户创建配额；重复调用保留最早创建时刻。
     * @return 已提交的共享空间或重复确认
     */
    CompletionStage<MemoryNamespaceMutation> ensureShared();
    /**
     * 创建由真实用户发起的新空间，幂等 ID 由原始请求确定；不冒充已切换绑定。
     * @param requestId 宿主固定请求 ID
     * @param actor 已由宿主认证和授权的真实来源
     * @return 等待配额检查和数据库提交的回执
     */
    CompletionStage<MemoryNamespaceMutation> create(UUID requestId, AgentActorContext actor);
    /**
     * 仅列举真实调用者自己创建的空间，不接受模型指定其他创建者 UUID。
     * @param actor 宿主认证的调用者
     * @param offset 页面偏移
     * @param limit 页面大小
     * @return 自己创建的已持久化页面；系统来源明确失败
     */
    CompletionStage<List<MemoryNamespace>> listCreated(AgentActorContext actor, int offset, int limit);
}
