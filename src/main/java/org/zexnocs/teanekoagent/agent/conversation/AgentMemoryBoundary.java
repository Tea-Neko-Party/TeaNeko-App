package org.zexnocs.teanekoagent.agent.conversation;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.agent.AgentHandle;
import org.zexnocs.teanekoagent.agent.AgentRunRequest;
import org.zexnocs.teanekoagent.agent.AgentRunResult;
import org.zexnocs.teanekoagent.agent.IAgentTurnService;
import org.zexnocs.teanekoagent.agent.access.AgentAccessDecision;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceReference;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.actuator.execution.ExecutionStoppedException;
import org.zexnocs.teanekocore.actuator.execution.ResourceBudget;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 管理邮箱引用操作的执行域、截止时间与关闭，不拥有会话窗口或持久化事务。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
final class AgentMemoryBoundary implements AutoCloseable {
    final IAgentMemoryReferences references;
    private final IAgentTurnService turns;
    private final Executor executor;
    private final ScheduledExecutorService scheduler;
    private final Clock clock;
    private final Set<ExecutionControl> owned = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * 保存借用资源，构造不读取配置或数据库。
     * @param references 当前引用端口
     * @param turns 只读准入端口
     * @param executor 业务执行器
     * @param scheduler 计时器
     * @param clock 可信时钟
     * @throws NullPointerException 当引用端口为空时
     */
    AgentMemoryBoundary(IAgentMemoryReferences references, IAgentTurnService turns, Executor executor,
            ScheduledExecutorService scheduler, Clock clock) {
        this.references = Objects.requireNonNull(references, "会话记忆引用端口不能为空。");
        this.turns = turns; this.executor = executor; this.scheduler = scheduler; this.clock = clock;
    }

    /**
     * 先检查真实活动准入再解析空间，不让未启用请求登记默认空间。
     * @param handle 实例
     * @param request 可信请求
     * @return 当前权威引用
     */
    CompletionStage<MemoryNamespaceReference> resolve(AgentHandle handle, AgentRunRequest request) {
        return access(handle, request).thenComposeAsync(ignored -> references.resolve(request.actor().scope()), executor);
    }

    /**
     * 前后复核活动准入，中间只读验证原引用，不刷新成新空间。
     * @param handle 实例
     * @param request 可信请求
     * @param expected 已固定引用
     * @return 当前边界均成立时完成
     */
    CompletionStage<Void> verify(AgentHandle handle, AgentRunRequest request, MemoryNamespaceReference expected) {
        return access(handle, request).thenComposeAsync(ignored -> references.verify(expected), executor)
                .thenComposeAsync(ignored -> access(handle, request), executor);
    }

    /**
     * 将准入拒绝转换为固定业务类别，不公开原配置或异常文本。
     * @param handle 实例
     * @param request 请求
     * @return 当前允许时完成
     */
    private CompletionStage<Void> access(AgentHandle handle, AgentRunRequest request) {
        return Objects.requireNonNull(turns.checkAccess(handle, request), "会话准入阶段不能为空。").thenAccept(decision -> {
            if (!decision.allowed()) { throw new Rejected(accessStatus(decision)); }
        });
    }

    /**
     * 在业务域发起操作并等待实际阶段；每个观察拥有自己的控制，不关闭借用父控制。
     * 自有控制跨异步回调存活，在完成链统一释放，不能在本方法返回时使用 try-with-resources 关闭。
     * @param parent 可选父控制
     * @param handle 可选实例关闭来源
     * @param deadline 本次绝对截止时间
     * @param action 生命周期锁外的异步操作
     * @param <T> 结果类型
     * @return 关闭自身监听及计时后的只读结果
     */
    <T> CompletionStage<T> await(@Nullable ExecutionControl parent, @Nullable AgentHandle handle, Instant deadline,
            Supplier<CompletionStage<T>> action) {
        return awaitControlled(parent, handle, deadline, ignored -> action.get());
    }

    /**
     * 向跨异步回调的状态提交提供实际观察控制，超时后的迟到允许不能修改会话。
     * 自有控制必须在完成链统一关闭，不在本方法返回时使用 try-with-resources。
     * @param parent 可选借用父控制
     * @param handle 可选实例关闭来源
     * @param deadline 绝对截止时间
     * @param action 使用实际控制复核提交时机的异步操作
     * @param <T> 结果类型
     * @return 自有控制回收后的只读完成阶段
     */
    @SuppressWarnings("resource")
    <T> CompletionStage<T> awaitControlled(@Nullable ExecutionControl parent, @Nullable AgentHandle handle, Instant deadline,
            Function<ExecutionControl, CompletionStage<T>> action) {
        final ExecutionControl control;
        try {
            if (parent != null) { parent.checkActive(); }
            control = ExecutionControl.withDeadline(parent == null ? new ResourceBudget(Map.of()) : parent.budget(), deadline, clock, scheduler);
        } catch (RuntimeException failure) { return CompletableFuture.failedStage(failure); }
        owned.add(control);
        var parentLink = parent == null ? null : parent.onTermination(ignored -> control.cancel());
        var handleLink = handle == null ? null : handle.onClose(control::cancel);
        if (closed.get()) { control.cancel(); }
        try {
            var work = CompletableFuture.supplyAsync(() -> {
                control.checkActive(); return Objects.requireNonNull(action.apply(control), "会话引用操作阶段不能为空。");
            }, executor).thenCompose(stage -> stage);
            return control.guard(work).handleAsync((value, failure) -> {
                if (parent != null) { parent.checkActive(); }
                if (failure != null) { throw new CompletionException(failure); }
                return value;
            }, executor).whenComplete((value, failure) -> {
                owned.remove(control);
                if (parentLink != null) { parentLink.close(); }
                if (handleLink != null) { handleLink.close(); }
                control.close();
            }).toCompletableFuture().minimalCompletionStage();
        } catch (RuntimeException failure) {
            owned.remove(control); if (parentLink != null) { parentLink.close(); }
            if (handleLink != null) { handleLink.close(); } control.close();
            return CompletableFuture.failedStage(failure);
        }
    }

    /**
     * 统一失败分类，不从异常正文推断权限或绑定。
     * @param failure 异步失败
     * @return 安全交付状态
     */
    static AgentRunResult.Status status(Throwable failure) {
        while ((failure instanceof CompletionException || failure instanceof ExecutionException) && failure.getCause() != null) { failure = failure.getCause(); }
        if (failure instanceof Rejected denied) { return denied.status; }
        if (failure instanceof ExecutionStoppedException stopped) {
            return stopped.reason() == ExecutionControl.StopReason.DEADLINE_EXCEEDED ? AgentRunResult.Status.TIMED_OUT : AgentRunResult.Status.CANCELLED;
        }
        if (failure instanceof AgentMemoryReferenceException memory) {
            return switch (memory.reason()) {
                case CHANGED -> AgentRunResult.Status.STALE_CONVERSATION;
                case DISABLED -> AgentRunResult.Status.SKIPPED;
                case UNAVAILABLE -> AgentRunResult.Status.CONFIGURATION_ERROR;
            };
        }
        return AgentRunResult.Status.CONFIGURATION_ERROR;
    }

    /**
     * 将原门控类别保持为业务状态。
     * @param decision 明确拒绝
     * @return 拒绝状态
     */
    static AgentRunResult.Status accessStatus(AgentAccessDecision decision) {
        return switch (decision.reason()) {
            case INSTANCE_CLOSED -> AgentRunResult.Status.CANCELLED;
            case READINESS_UNAVAILABLE, SCOPE_CONFIG_UNAVAILABLE -> AgentRunResult.Status.CONFIGURATION_ERROR;
            default -> AgentRunResult.Status.SKIPPED;
        };
    }

    /** 取消自己拥有的引用观察，不关闭借用执行器、父控制或存储。 */
    @Override public void close() { closed.set(true); owned.forEach(ExecutionControl::cancel); }

    /**
     * 保存非敏感准入结果，避免以异常文本跨执行域传递原因。
     * @author zExNocs
     * @date 2026/10/29
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private static final class Rejected extends RuntimeException {
        private static final long serialVersionUID = 1L;
        final AgentRunResult.Status status;
        /**
         * 保存稳定拒绝类别。
         * @param status 拒绝状态
         */
        Rejected(AgentRunResult.Status status) { super("当前 Agent 会话准入已失效。"); this.status = status; }
    }
}
