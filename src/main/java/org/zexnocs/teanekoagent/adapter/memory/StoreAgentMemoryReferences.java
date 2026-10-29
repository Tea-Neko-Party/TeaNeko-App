package org.zexnocs.teanekoagent.adapter.memory;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.agent.AgentScope;
import org.zexnocs.teanekoagent.agent.access.IAgentScopePolicyPort;
import org.zexnocs.teanekoagent.memory.namespace.IMemoryNamespaceBindingStore;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceMutation;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceReference;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceRegistry;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static org.zexnocs.teanekoagent.agent.memory.AgentMemoryReferenceException.Reason.*;

/**
 * 在借用业务执行域检查 scope，再经事务端口解析或复核引用；不阻塞等待数据库 Future。
 * 构造无副作用，默认共享空间只在已启用作用域的显式解析中按需登记。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Service
public final class StoreAgentMemoryReferences implements IAgentMemoryReferences {
    private final IAgentScopePolicyPort scopes;
    private final IMemoryNamespaceBindingStore bindings;
    private final MemoryNamespaceRegistry registry;
    private final Executor executor;

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
            return CompletableFuture.runAsync(() -> requireEnabled(scope), executor)
                    .thenCompose(ignored -> Objects.requireNonNull(operation.get(), "空间引用事务阶段不能为空。"))
                    .thenApplyAsync(value -> { requireEnabled(scope); return value; }, executor)
                    .exceptionallyCompose(failure -> CompletableFuture.failedStage(redacted(failure)))
                    .toCompletableFuture().minimalCompletionStage();
        } catch (RuntimeException failure) { return CompletableFuture.failedStage(redacted(failure)); }
    }

    /**
     * 读取真实 scope 配置，未登记或禁用不允许触碰空间存储。
     * @param scope 真实作用域
     * @throws AgentMemoryReferenceException 当 scope 未启用或配置不可用时
     */
    private void requireEnabled(AgentScope scope) {
        var policy = scopes.read(scope);
        if (!policy.registered() || !policy.enabled()) { throw new AgentMemoryReferenceException(DISABLED); }
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
