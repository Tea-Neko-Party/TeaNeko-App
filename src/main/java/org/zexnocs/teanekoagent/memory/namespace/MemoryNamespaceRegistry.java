package org.zexnocs.teanekoagent.memory.namespace;

import org.zexnocs.teanekoagent.agent.AgentActorContext;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 可信宿主使用的空间注册底层服务，固定创建身份并等待持久化；不切换 scope 或修改会话。
 * 上层须先检查当前启用状态及操作权限，本类没有聊天命令、模型工具或自动执行入口。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public final class MemoryNamespaceRegistry {
    private final IMemoryNamespaceStore store;
    private final IMemoryNamespaceDefaults defaults;
    private final Clock clock;

    /**
     * 保存依赖，不自动创建共享空间或访问数据库。
     * @param store 事务存储端口
     * @param defaults 懒读的本地配额
     * @param clock 创建时刻来源
     * @throws NullPointerException 当依赖为空时
     */
    public MemoryNamespaceRegistry(IMemoryNamespaceStore store, IMemoryNamespaceDefaults defaults, Clock clock) {
        this.store = Objects.requireNonNull(store, "记忆空间存储不能为空。");
        this.defaults = Objects.requireNonNull(defaults, "记忆空间配额策略不能为空。");
        this.clock = Objects.requireNonNull(clock, "记忆空间时钟不能为空。");
    }

    /**
     * 显式登记默认共享空间，不占用户创建配额；重复调用保留最早创建时刻。
     * @return 已提交的共享空间或重复确认
     */
    public CompletionStage<MemoryNamespaceMutation> ensureShared() {
        try { return store.create(new MemoryNamespace(MemoryNamespaceId.SHARED, 0, 0, clock.instant(), null, null), 1); }
        catch (RuntimeException failure) { return CompletableFuture.failedStage(failure); }
    }

    /**
     * 创建由真实用户发起的新空间，幂等 ID 由原始请求确定；不冒充已切换绑定。
     * @param requestId 宿主固定请求 ID
     * @param actor 已由宿主认证和授权的真实来源
     * @return 等待配额检查和数据库提交的回执
     */
    public CompletionStage<MemoryNamespaceMutation> create(UUID requestId, AgentActorContext actor) {
        try {
            var creation = new MemoryNamespace.Creation(requestId, actor);
            int quota = defaults.maxCreated();
            if (quota < 1) { throw new IllegalStateException("个人记忆空间配额必须为正整数。"); }
            return store.create(new MemoryNamespace(MemoryNamespaceId.created(requestId), 0, 0, clock.instant(), creation, null), quota);
        } catch (RuntimeException failure) { return CompletableFuture.failedStage(failure); }
    }

    /**
     * 仅列举真实调用者自己创建的空间，不接受模型指定其他创建者 UUID。
     * @param actor 宿主认证的调用者
     * @param offset 页面偏移
     * @param limit 页面大小
     * @return 自己创建的已持久化页面；系统来源明确失败
     */
    public CompletionStage<List<MemoryNamespace>> listCreated(AgentActorContext actor, int offset, int limit) {
        try {
            Objects.requireNonNull(actor, "记忆空间调用者不能为空。");
            if (actor.origin() != AgentActorContext.Origin.USER) { throw new IllegalArgumentException("系统任务不能冒充用户列举自建记忆空间。"); }
            return store.listCreated(actor.userId().orElseThrow(), offset, limit);
        } catch (RuntimeException failure) { return CompletableFuture.failedStage(failure); }
    }
}
