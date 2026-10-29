package org.zexnocs.teanekoagent.memory.namespace;

import org.zexnocs.teanekoagent.agent.AgentActorContext;
import org.zexnocs.teanekoagent.agent.AgentScope;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 可信宿主的绑定事务服务；上层先校验启用状态及 OWNER/私聊管理权限，提交后再协调会话。
 * 不复制 ConfigData 权威绑定，不注册聊天指令，也不把持久化回执当成当前运行授权。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public final class MemoryNamespaceBindings {
    private final IMemoryNamespaceBindingStore store;
    private final IMemoryNamespaceDefaults defaults;
    private final Clock clock;

    /**
     * 保存事务端口，不在装配期间读取配置、绑定空间或访问数据库。
     * @param store 绑定事务存储
     * @param defaults 本地创建配额
     * @param clock 原始操作时刻来源
     * @throws NullPointerException 当必需依赖为空时
     */
    public MemoryNamespaceBindings(IMemoryNamespaceBindingStore store, IMemoryNamespaceDefaults defaults, Clock clock) {
        this.store = Objects.requireNonNull(store, "空间绑定存储不能为空。");
        this.defaults = Objects.requireNonNull(defaults, "空间创建配额策略不能为空。");
        this.clock = Objects.requireNonNull(clock, "空间绑定时钟不能为空。");
    }

    /**
     * 读取当前权威引用，不创建默认空间；配置及权限判断仍由上层负责。
     * @param scope 真实作用域
     * @return 当前已登记空间的事务引用观察
     */
    public CompletionStage<Optional<MemoryNamespaceReference>> readReference(AgentScope scope) {
        try { return store.readReference(scope); }
        catch (RuntimeException failure) { return CompletableFuture.failedStage(failure); }
    }

    /**
     * 用同一事务创建新空间、占用配额并修改真实发起 scope 的绑定。
     * @param requestId 原始请求 ID
     * @param actor 已经上层认证授权的操作者
     * @param expectedRevision 原始预期绑定版本，重试保留
     * @return 已提交的操作回执；尚未同步会话，不直接作为平台已切换反馈
     */
    public CompletionStage<MemoryNamespaceBindingOutcome> createAndBind(UUID requestId, AgentActorContext actor, long expectedRevision) {
        try {
            var request = new MemoryNamespaceBindingRequest(requestId, actor, MemoryNamespaceId.created(requestId), expectedRevision,
                    MemoryNamespaceBindingRequest.Mode.CREATE_AND_BIND);
            int quota = defaults.maxCreated();
            if (quota < 1) { throw new IllegalStateException("个人记忆空间配额必须为正整数。"); }
            return store.apply(request, quota, clock.instant());
        } catch (RuntimeException failure) { return CompletableFuture.failedStage(failure); }
    }

    /**
     * 绑定已有空间，不修改原创建来源，不要求目标属于当前操作者，也不读取创建配额。
     * @param requestId 原始操作 ID
     * @param actor 经上层认证授权的操作者
     * @param target 允许绑定的已有业务空间
     * @param expectedRevision 原始预期版本
     * @return 持久化后的绑定或明确拒绝回执
     */
    public CompletionStage<MemoryNamespaceBindingOutcome> bindExisting(UUID requestId, AgentActorContext actor,
            MemoryNamespaceId target, long expectedRevision) {
        try {
            return store.apply(new MemoryNamespaceBindingRequest(requestId, actor, target, expectedRevision,
                    MemoryNamespaceBindingRequest.Mode.BIND_EXISTING), 1, clock.instant());
        } catch (RuntimeException failure) { return CompletableFuture.failedStage(failure); }
    }
}
