package org.zexnocs.teanekoplugin.agent.cognition.memory.adapter;

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoplugin.agent.cognition.memory.namespace.IMemoryNamespaceBindingStore;
import org.zexnocs.teanekoplugin.agent.cognition.memory.namespace.MemoryNamespaceMutation;
import org.zexnocs.teanekoplugin.agent.cognition.memory.namespace.MemoryNamespaceReference;
import org.zexnocs.teanekoplugin.agent.cognition.memory.namespace.MemoryNamespaceRegistry;
import org.zexnocs.teanekoplugin.agent.cognition.memory.runtime.AgentMemoryReferenceException;
import org.zexnocs.teanekoplugin.agent.cognition.memory.runtime.IAgentMemoryReferences;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekoagent.runtime.access.IAgentScopePolicyPort;
import org.zexnocs.teanekocore.plugin.PluginRegistry;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static org.zexnocs.teanekoplugin.agent.cognition.memory.runtime.AgentMemoryReferenceException.Reason.*;

/**
 * 在借用业务执行域检查 scope，再经事务端口解析或复核引用；不阻塞等待数据库 Future。
 * 构造无副作用，默认共享空间只在已启用作用域的显式解析中按需登记。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 * <br>2026/11/07：提供类型化可选资格，停用及旧代结果在触碰存储前后明确拒绝。
 *
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
@Service
@org.zexnocs.teanekocore.plugin.PluginOwned("agent.cognition")
public final class StoreAgentMemoryReferences implements IAgentMemoryReferences {
    private final IAgentScopePolicyPort scopes;
    private final IMemoryNamespaceBindingStore bindings;
    private final MemoryNamespaceRegistry registry;
    private final Executor executor;
    private @Nullable PluginRegistry plugins;

    /**
     * 2026/11/07：正式容器必须安装真实插件目录，独立端口测试保留显式构造方式。
     * @param plugins 实际插件目录
     */
    @Autowired
    public void installPlugins(PluginRegistry plugins) {
        this.plugins = plugins;
    }

    /**
     * 保存基础设施，不在 Spring 装配时读取配置或创建空间。
     * @param scopes 真实 scope 登记与开关
     * @param bindings 当前绑定及 generation 事务端口
     * @param registry 显式默认空间登记服务
     * @param executor 借用业务执行器
     * @throws NullPointerException 当必需依赖为空时
     */
    public StoreAgentMemoryReferences(IAgentScopePolicyPort scopes, IMemoryNamespaceBindingStore bindings,
            MemoryNamespaceRegistry registry, @Qualifier("virtualExecutor") Executor executor) {
        this.scopes = Objects.requireNonNull(scopes, "空间引用作用域端口不能为空。");
        this.bindings = Objects.requireNonNull(bindings, "空间引用事务端口不能为空。");
        this.registry = Objects.requireNonNull(registry, "空间注册服务不能为空。");
        this.executor = Objects.requireNonNull(executor, "空间引用业务执行器不能为空。");
    }

    /**
     * 2026/11/07：仅从插件目录和当前 scope 快照检查资格，不解析或创建持久空间。
     * @param scope 真实作用域
     * @return 认知插件有效且此作用域当前装配记忆能力时为真
     */
    @Override
    public boolean available(AgentScope scope) {
        if (plugins != null && !plugins.isEnabled("agent.cognition")) {
            return false;
        }
        var policy = scopes.read(scope);
        return policy.registered() && policy.enabled() && policy.assembly().has("memory");
    }

    /**
     * 解析已启用 scope 的当前引用；只有隐式默认空间尚未登记时才尝试初始化。
     * @param scope 真实作用域
     * @return 当前引用，存储失败不降级为虚构默认值
     */
    @Override public CompletionStage<MemoryNamespaceReference> resolve(AgentScope scope) {
        return checked(scope, () -> bindings.readReference(scope).thenCompose(value -> {
            if (value.isPresent()) { return CompletableFuture.completedStage(validScope(scope, value.orElseThrow())); }
            return CompletableFuture.runAsync(() -> requireEnabled(scope), executor).thenCompose(ignored -> registry.ensureShared())
                    .thenCompose(result -> {
                        if (result.status() != MemoryNamespaceMutation.Status.CREATED && result.status() != MemoryNamespaceMutation.Status.UNCHANGED) {
                            throw new AgentMemoryReferenceException(UNAVAILABLE);
                        }
                        return bindings.readReference(scope);
                    }).thenApply(latest -> validScope(scope, latest.orElseThrow(() -> new AgentMemoryReferenceException(UNAVAILABLE))));
        }));
    }

    /**
     * 只读检查原引用，不在记录缺失时自动创建默认空间，也不把最新引用替换进旧轮次。
     * @param expected 原始引用
     * @return 一致时完成，scope 停用、缺失或变更时明确失败
     * @throws NullPointerException 当原始引用为空时
     */
    @Override public CompletionStage<Void> verify(MemoryNamespaceReference expected) {
        Objects.requireNonNull(expected, "原始记忆空间引用不能为空。");
        return checked(expected.scope(), () -> bindings.readReference(expected.scope()).thenApply(value -> {
            var current = validScope(expected.scope(), value.orElseThrow(() -> new AgentMemoryReferenceException(UNAVAILABLE)));
            if (!current.equals(expected)) { throw new AgentMemoryReferenceException(CHANGED); }
            return null;
        }));
    }

    /**
     * 业务域检查前后开关，转换为固定原因；配置、数据库和用户回调不进入生命周期锁。
     * @param scope 真实作用域
     * @param operation 异步事务操作
     * @param <T> 只读结果类型
     * @return 不导出原异常链的只读阶段
     */
    private <T> CompletionStage<T> checked(AgentScope scope, Supplier<CompletionStage<T>> operation) {
        try {
            Objects.requireNonNull(scope, "记忆空间作用域不能为空。");
            // 2026/11/07：在首次实际读取前固定插件代次，停用再启用不能交付旧代引用。
            return CompletableFuture.supplyAsync(() -> {
                        requireEnabled(scope);
                        return plugins == null ? null : plugins.capture("agent.cognition");
                    }, executor)
                    .thenCompose(token -> Objects.requireNonNull(operation.get(), "空间引用事务阶段不能为空。")
                            .thenApplyAsync(value -> {
                                requireEnabled(scope);
                                if (token != null) {
                                    try {
                                        Objects.requireNonNull(plugins).check(token);
                                    } catch (IllegalStateException changed) {
                                        throw new AgentMemoryReferenceException(CHANGED);
                                    }
                                }
                                return value;
                            }, executor))
                    .exceptionallyCompose(failure -> CompletableFuture.failedStage(redacted(failure)))
                    .toCompletableFuture().minimalCompletionStage();
        } catch (RuntimeException failure) { return CompletableFuture.failedStage(redacted(failure)); }
    }

    /**
     * 2026/11/07：读取实时插件与 scope 资格，未选择或禁用能力不允许触碰空间存储。
     * @param scope 真实作用域
     * @throws AgentMemoryReferenceException 当记忆能力未启用或配置不可用时
     */
    private void requireEnabled(AgentScope scope) {
        if (!available(scope)) {
            throw new AgentMemoryReferenceException(DISABLED);
        }
    }

    /**
     * 验证端口返回的完整 client/scope，拒绝错误适配器的跨域引用。
     * @param scope 原始作用域
     * @param value 当前引用
     * @return 定位一致的引用
     * @throws AgentMemoryReferenceException 当端口返回错误作用域时
     */
    private static MemoryNamespaceReference validScope(AgentScope scope, MemoryNamespaceReference value) {
        if (!scope.equals(value.scope())) { throw new AgentMemoryReferenceException(UNAVAILABLE); }
        return value;
    }

    /**
     * 保留类型化拒绝，其他故障使用固定原因，不复制凭据或存储正文。
     * @param failure 观察到的阶段失败
     * @return 不带原异常链的引用异常
     */
    private static AgentMemoryReferenceException redacted(Throwable failure) {
        while ((failure instanceof CompletionException || failure instanceof ExecutionException) && failure.getCause() != null) { failure = failure.getCause(); }
        return failure instanceof AgentMemoryReferenceException known ? known : new AgentMemoryReferenceException(UNAVAILABLE);
    }
}
