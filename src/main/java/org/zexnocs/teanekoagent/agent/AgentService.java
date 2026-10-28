package org.zexnocs.teanekoagent.agent;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.agent.access.AgentAccessGate;
import org.zexnocs.teanekoagent.llm.framework.interaction.ILLMInteractionService;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.framework.composition.EntityId;
import org.zexnocs.teanekocore.framework.composition.SystemPipeline;
import org.zexnocs.teanekocore.logger.ILogger;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.Function;

/**
 * 独立轮次的异步入口及资源拥有者，复用预编译管线与既有 LLM 交互服务。
 * 同实例可执行不同轮次；本类不冒充尚未接入的会话队列、历史或持久化幂等。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public final class AgentService implements IAgentTurnService, AutoCloseable {
    private final AgentAccessGate gate;
    private final Executor executor;
    private final ScheduledExecutorService scheduler;
    private final Clock clock;
    private final ILogger logger;
    private final ConcurrentMap<EntityId, AgentTurn> turns = new ConcurrentHashMap<>();
    private final Object lifecycleLock = new Object();
    private final SystemPipeline pipeline;
    private boolean closed;

    /**
     * 装配空服务并预编译管线，不读取配置、创建实例或发起业务。
     * @param gate 全活动准入门控
     * @param interactions 完整 LLM 交互端口
     * @param tools 显式本轮工具选择端口
     * @param mapper JSON 校验器
     * @param executor 借用业务执行器
     * @param scheduler 借用计时器
     * @param clock 独立请求时钟
     * @param logger 项目诊断日志
     */
    public AgentService(AgentAccessGate gate, ILLMInteractionService interactions, IAgentToolSource tools,
            ObjectMapper mapper, Executor executor, ScheduledExecutorService scheduler, Clock clock, ILogger logger) {
        this.gate = Objects.requireNonNull(gate, "Agent 门控不能为空。");
        this.executor = Objects.requireNonNull(executor, "业务执行器不能为空。");
        this.scheduler = Objects.requireNonNull(scheduler, "计时器不能为空。");
        this.clock = Objects.requireNonNull(clock, "轮次时钟不能为空。");
        this.logger = Objects.requireNonNull(logger, "日志端口不能为空。");
        pipeline = new AgentTurnPipeline(this::requireTurn, gate,
                Objects.requireNonNull(interactions, "交互端口不能为空。"), Objects.requireNonNull(tools, "工具源不能为空。"),
                Objects.requireNonNull(mapper, "JSON 校验器不能为空。"), executor).pipeline;
    }

    /**
     * 建立具有独立总预算的轮次，所有配置检查切入业务执行域。
     * @param handle 目标实例
     * @param request 固定宿主请求
     * @return 真正完成的只读业务阶段
     * @throws NullPointerException 当实例或请求为空时
     */
    public CompletionStage<AgentRunResult> run(AgentHandle handle, AgentRunRequest request) {
        return prepare(handle, request).start();
    }

    /**
     * 为宿主请求建立共享预算的子轮次，关闭不反向影响父执行。
     * @param handle 目标实例
     * @param request 固定宿主请求
     * @param parent 借用的总控制，必须已声明 LLM 所需维度
     * @return 真正完成的只读业务阶段
     * @throws NullPointerException 当实例、请求或父控制为空时
     */
    public CompletionStage<AgentRunResult> run(AgentHandle handle, AgentRunRequest request, ExecutionControl parent) {
        return prepare(handle, request, parent).start();
    }

    /**
     * 登记可取消的独立轮次，显式启动前不读取配置或执行模型。
     * @param handle 目标实例
     * @param request 固定宿主请求
     * @return 尚未执行业务的句柄
     * @throws NullPointerException 当实例或请求为空时
     */
    @Override public AgentRunExecution prepare(AgentHandle handle, AgentRunRequest request) {
        return prepareInternal(handle, request, null);
    }

    /**
     * 登记共享宿主预算的冷轮次，不新建父总额度。
     * @param handle 目标实例
     * @param request 固定宿主请求
     * @param parent 借用的父执行控制
     * @return 可在启动前取消的句柄
     * @throws NullPointerException 当实例、请求或父控制为空时
     */
    @Override public AgentRunExecution prepare(AgentHandle handle, AgentRunRequest request, ExecutionControl parent) {
        return prepareInternal(handle, request, Objects.requireNonNull(parent, "派生轮次的父控制不能为空。"));
    }

    /**
     * 登记当前拥有关系和完成观察器，业务准备由返回句柄显式启动。
     * @param handle 目标实例
     * @param request 已固定请求
     * @param parent 可选借用父控制
     * @return 已登记取消和完成观察的冷执行句柄；跨实例的会话绑定在分配控制前拒绝
     * @throws NullPointerException 当实例或请求为空时
     */
    private AgentRunExecution prepareInternal(AgentHandle handle, AgentRunRequest request, @Nullable ExecutionControl parent) {
        Objects.requireNonNull(handle, "Agent 实例不能为空。");
        Objects.requireNonNull(request, "Agent 请求不能为空。");
        if (request.activity() == AgentActivity.COLLECT || request.activity() == AgentActivity.TOOL
                || request.conversation() != null && !request.conversation().key().agentId().equals(handle.id())) {
            return completed(new AgentRunResult(handle.id(), request.requestId(),
                    AgentRunResult.Status.INVALID_INPUT, Optional.empty(), Optional.empty(), Map.of()));
        }
        final AgentTurn turn;
        try {
            var acceptedAt = clock.instant();
            turn = new AgentTurn(handle, request, parent == null ? ExecutionControl.create() : parent.child(), parent == null, acceptedAt);
        } catch (RuntimeException failure) {
            return completed(new AgentRunResult(handle.id(), request.requestId(),
                    AgentTurn.failureStatus(failure), Optional.empty(), Optional.empty(),
                    parent == null ? Map.of() : parent.budget().snapshot()));
        }
        final boolean accepted;
        synchronized (lifecycleLock) {
            accepted = !closed;
            if (accepted) { turns.put(turn.entity.snapshot().id(), turn); }
        }
        if (!accepted) {
            turn.lifetime.cancel();
            var result = turn.result(null);
            turn.close();
            return completed(result);
        }
        turn.own(handle.onClose(turn.lifetime::cancel));
        var work = new CompletableFuture<>();
        var completion = turn.lifetime.guard(work).handle((ignored, failure) -> turn.settled().thenApply(done -> {
            var result = turn.result(failure);
            if (result.status() == AgentRunResult.Status.FAILED) { reportFailure(); }
            return result;
        })).thenCompose(Function.identity()).whenComplete((ignored, failure) -> {
            turns.remove(turn.entity.snapshot().id(), turn);
            turn.close();
        }).toCompletableFuture().minimalCompletionStage();
        return new AgentRunExecution(completion, () -> launch(turn, work), turn.lifetime::cancel, turn::contains);
    }

    /**
     * 在观察器登记后异步准备和执行，未启动取消不会触发任何业务端口。
     * @param turn 当前资源拥有者
     * @param work 用于驱动统一完成路径的内部信号
     */
    private void launch(AgentTurn turn, CompletableFuture<Object> work) {
        try {
            turn.lifetime.checkActive();
            CompletableFuture.supplyAsync(() -> {
                turn.lifetime.checkActive();
                return turn.prepare(gate.evaluate(turn.handle, turn.request.actor(), turn.request.activity()), scheduler, clock);
            }, executor).thenComposeAsync(prepared -> {
                turn.lifetime.checkActive();
                return prepared ? turn.control().guard(pipeline.execute(turn.entity, turn.control()))
                        : CompletableFuture.completedStage(null);
            }, executor).whenComplete((value, failure) -> {
                if (failure == null) { work.complete(value); } else { work.completeExceptionally(failure); }
            });
        } catch (RuntimeException failure) { work.completeExceptionally(failure); }
    }

    /**
     * 为准备前拒绝提供已结束句柄，不创建运行资源。
     * @param result 明确终止结果
     * @return 启动和取消均无业务动作的句柄
     */
    private static AgentRunExecution completed(AgentRunResult result) {
        return new AgentRunExecution(CompletableFuture.completedStage(result), () -> {}, () -> false, ignored -> false);
    }

    /**
     * 仅查找本服务登记的资源拥有者，不提供任意服务定位。
     * @param id 管线实体标识
     * @return 当前拥有者
     * @throws IllegalStateException 当轮次已经回收或不属于本服务时
     */
    private AgentTurn requireTurn(EntityId id) {
        var turn = turns.get(id);
        if (turn == null) { throw new IllegalStateException("Agent 轮次已回收或未由当前服务登记。"); }
        return turn;
    }

    /** 记录固定中文原因，诊断故障不改变业务完成结果。 */
    private void reportFailure() {
        try { logger.error(AgentService.class.getName(), "Agent 轮次执行失败，请检查端口装配和运行状态。"); }
        catch (RuntimeException ignored) { /* 不能因诊断失败而丢失轮次结果。 */ }
    }

    /** 关闭本服务在途轮次，回调在拥有关系锁外执行，不关闭实例、父请求或借用线程池。 */
    @Override public void close() {
        final List<AgentTurn> active;
        synchronized (lifecycleLock) {
            if (closed) { return; }
            closed = true;
            active = List.copyOf(turns.values());
        }
        active.forEach(turn -> turn.lifetime.cancel());
    }
}
