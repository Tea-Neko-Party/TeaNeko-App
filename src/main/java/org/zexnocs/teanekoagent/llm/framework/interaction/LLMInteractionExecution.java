package org.zexnocs.teanekoagent.llm.framework.interaction;

import org.zexnocs.teanekoagent.llm.framework.input.LLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.input.interfaces.ILLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.message.LLMProtocolSnapshots;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelService;
import org.zexnocs.teanekoagent.llm.framework.response.interfaces.ILLMResult;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMToolSet;
import org.zexnocs.teanekoagent.llm.framework.tool.ToolInvocationContext;
import org.zexnocs.teanekoagent.llm.framework.tool.ToolInvocationResult;
import org.zexnocs.teanekoagent.llm.framework.tool.interfaces.ILLMToolCall;
import org.zexnocs.teanekocore.actuator.execution.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.zexnocs.teanekoagent.llm.framework.interaction.LLMCallLimits.*;

/**
 * 单个交互的有界阶段执行器，统一模型、顺序工具、父共享预算及阶段资源生命周期。
 * 拥有者须关闭本实例；注入的执行器、调度器和父控制均不归本实例所有。
 * 此类型不自动循环、不提交业务历史，模型响应与工具调用的关联由上层编排验证。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 * @see LLMCallLimits
 */
public final class LLMInteractionExecution implements AutoCloseable {
    private final ILLMModelService models;
    private final LLMToolSet tools;
    private final ToolInvocationContext context;
    private final LLMInteractionSettings settings;
    private final Executor executor;
    private final ScheduledExecutorService scheduler;
    private final ResourceBudget localBudget;
    private final ExecutionControl control;
    private final ILLMTokenEstimator tokenEstimator;
    private final LLMTokenBudget tokenBudget;
    private final Map<Integer, LLMInteractionResult.ModelAttempt> modelAttempts = new ConcurrentHashMap<>();
    private final AtomicBoolean busy = new AtomicBoolean();
    private final Set<String> toolIds = new HashSet<>();
    private final List<ToolInvocationResult> completedTools = new ArrayList<>();
    private volatile boolean toolRoundAvailable;

    /**
     * 固定本次交互配置和工具集，建立独立整轮截止时间并继承宿主总预算。
     * 父预算必须显式声明三个 LLM 资源维度，缺失时拒绝装配，不自动创建新总额度。
     *
     * @param models 单次模型调用端口
     * @param tools 本轮已授权且固定的工具集合
     * @param context 宿主提供的请求标识、业务绑定与父控制
     * @param settings 本次次数及时间上限
     * @param executor 借用的异步业务执行器，不得内联执行可能阻塞的业务，也不得由本实例关闭
     * @param scheduler 借用的截止时间调度器
     * @throws NullPointerException 当任一依赖为空时
     * @throws IllegalArgumentException 当父预算缺少必需维度时
     * @throws ExecutionStoppedException 当父执行已经终止时
     * @throws RuntimeException 当调度器无法建立整轮计时任务时
     */
    public LLMInteractionExecution(ILLMModelService models, LLMToolSet tools, ToolInvocationContext context,
                                   LLMInteractionSettings settings, Executor executor,
                                   ScheduledExecutorService scheduler) {
        this(models, tools, context, settings, executor, scheduler,
                new JsonLLMTokenEstimator(new tools.jackson.databind.ObjectMapper()));
    }

    /**
     * 注入可替换输入估算器，固定父预算并为可选 token 维度建立局部上限。
     * 局部未声明 token 上限仍继承宿主限制；局部声明而宿主缺少维度时拒绝执行。
     * @param models 单次模型端口
     * @param tools 本轮可信工具集
     * @param context 宿主身份、预算及父控制
     * @param settings 本轮固定设置
     * @param executor 借用业务执行器
     * @param scheduler 借用计时器
     * @param tokenEstimator 不发起外部请求的输入估算器
     * @throws IllegalArgumentException 当宿主缺少必需预算维度时
     */
    public LLMInteractionExecution(ILLMModelService models, LLMToolSet tools, ToolInvocationContext context,
                                   LLMInteractionSettings settings, Executor executor,
                                   ScheduledExecutorService scheduler, ILLMTokenEstimator tokenEstimator) {
        this.models = Objects.requireNonNull(models, "模型端口不能为空。");
        this.tools = Objects.requireNonNull(tools, "工具集合不能为空。");
        this.settings = Objects.requireNonNull(settings, "交互配置不能为空。");
        this.executor = Objects.requireNonNull(executor, "业务执行器不能为空。");
        this.scheduler = Objects.requireNonNull(scheduler, "计时调度器不能为空。");
        this.tokenEstimator = Objects.requireNonNull(tokenEstimator, "token 估算器不能为空。");
        Objects.requireNonNull(context, "宿主上下文不能为空。");
        for (var key : List.of(MODEL_REQUESTS, TOOL_ROUNDS, TOOL_EXECUTIONS)) {
            context.control().budget().remaining(key);
        }
        if (settings.limits().maxTotalTokens() != null) { context.control().budget().remaining(TOTAL_TOKENS); }
        var limits = new HashMap<BudgetKey, Long>();
        settings.limits().newBudget().snapshot().forEach((key, value) -> limits.put(key, value.limit()));
        if (context.control().budget().isDeclared(TOTAL_TOKENS)) {
            limits.putIfAbsent(TOTAL_TOKENS, context.control().budget().snapshot().get(TOTAL_TOKENS).limit());
        }
        localBudget = new ResourceBudget(limits);
        tokenBudget = new LLMTokenBudget(context.control().budget(), localBudget);
        control = context.control().child(settings.turnTimeout(), scheduler);
        this.context = context.withControl(control);
    }

    /**
     * 在模型阶段开始前预占一次请求；失败的新尝试仍计数，取消的排队任务不会发起调用。
     * 可见工具始终取自本轮绑定，局部或共享额度只剩末次请求时强制无工具收束。
     * 在排队前冻结消息及嵌套元数据；调用方只须避免在复制期间并发修改原对象。
     * 启用 token 预算时先预检参数及估算输入，预留成功才计模型尝试；完成后按报告结算。
     *
     * @param prompt 本次显式消息与模型参数，不在此处自动追加历史
     * @return 单次模型请求实际完成的只读阶段；预算、取消或传输失败异常完成
     */
    public CompletionStage<ILLMResult> callModel(ILLMPrompt prompt) {
        return exclusive(() -> {
            Objects.requireNonNull(prompt, "模型输入不能为空。");
            var messages = LLMProtocolSnapshots.messages(prompt.getMessages());
            var options = LLMProtocolSnapshots.options(prompt.getOptions());
            var held = new AtomicReference<LLMTokenBudget.Held>();
            var attempt = new AtomicInteger();
            toolRoundAvailable = false;
            CompletionStage<ILLMResult> pendingStage = phase(settings.modelTimeout(), stage -> {
                LLMModelOptions prepared = options;
                LLMTokenEstimate estimate = null;
                if (tokenBudget.enabled()) {
                    tokenBudget.requireAvailable();
                    boolean preview = remaining(MODEL_REQUESTS) > 1 && remaining(TOOL_ROUNDS) > 0
                            && remaining(TOOL_EXECUTIONS) > 0 && !tools.definitions().isEmpty();
                    options.setTools(preview ? tools.definitions() : List.of());
                    options.setToolChoice(preview ? options.findToolChoice().orElse("auto") : "none");
                    var resolved = Objects.requireNonNull(models.resolveOptions(new LLMPrompt(messages, options)), "有效模型参数不能为空。");
                    prepared = LLMProtocolSnapshots.options(resolved);
                    prepared.setTools(options.getTools());
                    prepared.setToolChoice(options.getToolChoice());
                    estimate = Objects.requireNonNull(tokenEstimator.estimate(new LLMPrompt(messages, prepared)), "token 估算不能为空。");
                }
                var effective = prepared;
                var inputEstimate = estimate;
                var allowed = stage.whileActive(() -> {
                    requireCapacity(MODEL_REQUESTS, 1);
                    var reserved = tokenBudget.enabled() ? tokenBudget.reserve(inputEstimate, effective) : null;
                    try {
                        reserve(MODEL_REQUESTS);
                    } catch (RuntimeException failure) {
                        if (reserved != null) { reserved.close(); }
                        throw failure;
                    }
                    held.set(reserved);
                    attempt.set(Math.toIntExact(localBudget.used(MODEL_REQUESTS)));
                    return canOfferTools();
                });
                effective.setTools(allowed ? tools.definitions() : List.of());
                effective.setToolChoice(allowed ? effective.findToolChoice().orElse("auto") : "none");
                var pending = Objects.requireNonNull(models.call(new LLMPrompt(messages, effective),
                        ExecutionPolicy.controlled(settings.modelTimeout(), stage)), "模型端口返回的任务不能为空。");
                return pending.asStage().thenApply(result -> stage.whileActive(() -> {
                    if (result == null) { throw new InvalidModelResponseException(); }
                    toolRoundAvailable = allowed && !"none".equals(effective.getToolChoice());
                    return result;
                }));
            });
            return pendingStage.handle((result, failure) -> completeModel(attempt.get(), held.get(), result, failure));
        });
    }

    /**
     * 在唯一阶段完成信号上冻结结果并结算预留；取消后的迟到响应不会再次记账。
     * @param attempt 实际尝试序号，尚未进入模型端口时为零
     * @param held 可选 token 预留
     * @param result 阶段成功响应
     * @param failure 阶段失败原因
     * @return 已冻结单次响应
     * @throws BudgetExceededException 当实际结算超出任一级总额时
     * @throws CompletionException 当阶段失败或响应非法时
     */
    private ILLMResult completeModel(int attempt, LLMTokenBudget.Held held, ILLMResult result, Throwable failure) {
        ILLMResult snapshot = null;
        if (result != null) {
            try { snapshot = LLMProtocolSnapshots.result(result); }
            catch (RuntimeException invalid) { failure = new InvalidModelResponseException(); }
        }
        var usage = tokenBudget.settle(held, snapshot);
        if (attempt > 0) {
            modelAttempts.put(attempt, new LLMInteractionResult.ModelAttempt(attempt, Optional.ofNullable(snapshot), usage));
        }
        if (failure != null) { throw new CompletionException(unwrap(failure)); }
        if (usage.map(LLMTokenUsage::exceeded).orElse(false)) {
            toolRoundAvailable = false;
            var budget = localBudget.snapshot().get(TOTAL_TOKENS).exceeded() ? localBudget : control.budget();
            throw tokenBudget.exceeded(budget, 0);
        }
        return snapshot;
    }

    /**
     * 取得某次实际模型尝试的固定响应与记账依据，即使该次因超额而异常结束也保留记录。
     * @param number 从一开始的尝试序号
     * @return 阶段已经结束时的记录，尚未发起或完成时为空
     */
    public Optional<LLMInteractionResult.ModelAttempt> modelAttempt(int number) {
        return Optional.ofNullable(modelAttempts.get(number));
    }

    /**
     * 在最近一次开放工具的模型阶段之后，顺序执行至多一个工具批次并等待每个结果。
     * 批次开始前校验调用格式、重复 ID 和可用额度；阶段超时可回填，整轮终止则停止余项。
     * 本实例内重复 ID 被拒绝，跨请求的副作用幂等仍由业务执行记录服务负责。
     *
     * @param calls 已由上层与模型响应关联的有序工具调用
     * @return 顺序不变的不可变回填列表；整体取消、预算耗尽或非法批次异常完成
     */
    public CompletionStage<List<ToolInvocationResult>> executeTools(List<? extends ILLMToolCall> calls) {
        return exclusive(() -> {
            var snapshot = snapshotCalls(calls);
            if (snapshot.isEmpty()) {
                return CompletableFuture.completedStage(List.of());
            }
            control.whileActive(() -> {
                tokenBudget.requireAvailable();
                requireCapacity(MODEL_REQUESTS, 1);
                if (!toolRoundAvailable) {
                    throw new ToolRoundUnavailableException();
                }
                requireCapacity(TOOL_ROUNDS, 1);
                requireCapacity(TOOL_EXECUTIONS, snapshot.size());
                toolRoundAvailable = false;
                snapshot.forEach(call -> toolIds.add(call.getId()));
                return null;
            });
            CompletionStage<List<ToolInvocationResult>> chain = CompletableFuture.completedStage(new ArrayList<>());
            var first = new AtomicBoolean(true);
            for (var call : snapshot) {
                chain = chain.thenCompose(results -> phase(settings.toolTimeout(), stage -> {
                    stage.whileActive(() -> {
                        tokenBudget.requireAvailable();
                        if (first.compareAndSet(true, false)) {
                            reserve(TOOL_ROUNDS);
                        }
                        reserve(TOOL_EXECUTIONS);
                        return null;
                    });
                    return tools.execute(call, context.withControl(stage));
                }).handle((result, failure) -> {
                    control.checkActive();
                    if (failure != null) {
                        var cause = unwrap(failure);
                        if (cause instanceof BudgetExceededException) {
                            throw new CompletionException(cause);
                        }
                        var status = cause instanceof ExecutionStoppedException stopped
                                && stopped.reason() == ExecutionControl.StopReason.DEADLINE_EXCEEDED
                                ? ToolInvocationResult.Status.TIMED_OUT : ToolInvocationResult.Status.FAILED;
                        result = new ToolInvocationResult(call, status,
                                "{\"ok\":false,\"error\":{\"code\":\"" + status.name() + "\"}}", false);
                    }
                    results.add(result);
                    synchronized (completedTools) {
                        completedTools.add(result);
                    }
                    return results;
                }));
            }
            return chain.thenApply(List::copyOf);
        });
    }

    /**
     * 提供本实例实际预占的局部用量，不清零宿主预算，也不把缺失供应商 usage 伪装为 token 数据。
     *
     * @return 次数及可选 token 维度的不可变快照，token 包含尚未结算的预留
     */
    public Map<BudgetKey, ResourceBudget.Usage> usage() {
        return localBudget.snapshot();
    }

    /**
     * 返回已观察到完成的工具结果；后续工具失败不会抹去此前已完成动作的诊断记录。
     * 未返回结果的在途工具仍可能已有外部副作用，本记录不承诺回滚或推断其最终业务状态。
     *
     * @return 顺序不变的不可变结果列表，不包含执行上下文
     */
    public List<ToolInvocationResult> toolResults() {
        synchronized (completedTools) {
            return List.copyOf(completedTools);
        }
    }

    /**
     * 取消当前交互及其在途阶段，不反向取消宿主或其他交互。
     *
     * @return 首次取消成功时为 true，已经终止时为 false
     */
    public boolean cancel() {
        return control.cancel();
    }

    /**
     * 关闭本实例拥有的控制、截止时间任务和父监听，借用执行器保持可用。
     */
    @Override
    public void close() {
        control.close();
    }

    /**
     * 仅在存在下一次请求和工具额度时开放工具，防止末次请求产生不可回填的业务动作。
     *
     * @return 本轮存在工具、后续模型额度及工具额度时为 true
     */
    private boolean canOfferTools() {
        return !tools.definitions().isEmpty() && remaining(MODEL_REQUESTS) > 0
                && remaining(TOOL_ROUNDS) > 0 && remaining(TOOL_EXECUTIONS) > 0;
    }

    /**
     * 在交互串行边界内先确认局部额度，再扣减共享与局部预算，不创建新的宿主限额。
     *
     * @param key 本次需要预占一个单位的资源维度
     * @throws BudgetExceededException 当局部或宿主额度不足时
     */
    private void reserve(BudgetKey key) {
        requireCapacity(key, 1);
        control.consume(key, 1);
        localBudget.consume(key, 1);
    }

    /**
     * 取局部与宿主剩余额度的较小值，供工具开放与预占判断共同使用。
     *
     * @param key 需要观察的已声明维度
     * @return 实际可用额度，不修改任何计数
     */
    private long remaining(BudgetKey key) {
        return Math.min(localBudget.remaining(key), control.budget().remaining(key));
    }

    /**
     * 检查批次所需额度，不进行部分预占，避免明显超限的批次执行前几项副作用。
     *
     * @param key 需要检查的预算维度
     * @param units 所需正额度
     * @throws BudgetExceededException 当局部或共享可用额度不足时
     */
    private void requireCapacity(BudgetKey key, long units) {
        if (localBudget.remaining(key) < units) {
            var used = localBudget.used(key);
            throw new BudgetExceededException(key, used + localBudget.remaining(key), used, units);
        }
        if (control.budget().remaining(key) < units) {
            var used = control.budget().used(key);
            throw new BudgetExceededException(key, used + control.budget().remaining(key), used, units);
        }
    }

    /**
     * 为一个阶段建立独立截止时间；准备、排队和真实完成等待均受同一子控制约束。
     * 取消排队 Future 不强制中断任意同步方法，资源适配器仍须响应子控制终止。
     *
     * @param timeout 当前阶段上限
     * @param action 取得阶段控制后发起真实业务的回调
     * @param <T> 阶段业务结果类型
     * @return 在结果返回前关闭阶段控制的只读完成阶段
     * @throws ExecutionStoppedException 当交互在建立子控制前已经终止时
     * @throws RuntimeException 当计时调度器无法建立阶段截止时间时
     */
    private <T> CompletionStage<T> phase(Duration timeout, Function<ExecutionControl, CompletionStage<T>> action) {
        var stage = control.child(timeout, scheduler);
        try {
            var invocation = CompletableFuture.supplyAsync(() -> {
                stage.checkActive();
                return Objects.requireNonNull(action.apply(stage), "阶段回调返回的完成信号不能为空。");
            }, executor);
            return stage.guard(invocation.thenCompose(Function.identity())).whenComplete((value, failure) -> {
                stage.close();
                invocation.cancel(false);
            });
        } catch (RuntimeException failure) {
            stage.close();
            return CompletableFuture.failedStage(failure);
        }
    }

    /**
     * 拒绝同一交互内重叠的模型和工具操作，任何成功、失败或终止路径都释放在途标志。
     *
     * @param operation 本次阶段编排，不得阻塞等待其他阶段
     * @param <T> 编排结果类型
     * @return 串行操作的只读完成阶段，契约错误亦以异常完成
     */
    private <T> CompletionStage<T> exclusive(Supplier<CompletionStage<T>> operation) {
        if (!busy.compareAndSet(false, true)) {
            return CompletableFuture.failedStage(new IllegalStateException("同一交互已有在途阶段。"));
        }
        try {
            control.checkActive();
            return operation.get().whenComplete((value, failure) -> busy.set(false));
        } catch (RuntimeException failure) {
            busy.set(false);
            return CompletableFuture.failedStage(failure);
        }
    }

    /**
     * 冻结整批调用并在任何副作用前校验格式和重复 ID；模型参数不会进入可信上下文。
     *
     * @param calls 上层选定的有序工具请求
     * @return 与原始可变 DTO 独立的调用快照
     * @throws IllegalArgumentException 当调用格式或 ID 不符合协议时
     * @throws NullPointerException 当列表或调用为空时
     */
    private List<Call> snapshotCalls(List<? extends ILLMToolCall> calls) {
        var result = new ArrayList<Call>();
        var ids = new HashSet<>(toolIds);
        for (var source : Objects.requireNonNull(calls, "工具批次不能为空。")) {
            Objects.requireNonNull(source, "工具调用不能为空。");
            var call = new Call(source.getId(), source.getType(), source.getName(), source.getArguments());
            if (!"function".equals(call.getType()) || call.getId() == null || call.getId().isBlank()
                    || call.getName() == null || call.getName().isBlank() || !ids.add(call.getId())) {
                throw new IllegalArgumentException("工具调用格式非法或调用 ID 已使用。");
            }
            result.add(call);
        }
        return List.copyOf(result);
    }

    /**
     * 解开完成阶段包装，仅用于内部分类，原始文本不进入模型工具错误信封。
     *
     * @param failure 阶段观察到的异常
     * @return 不再被完成阶段包装的根异常
     */
    private static Throwable unwrap(Throwable failure) {
        while ((failure instanceof CompletionException || failure instanceof ExecutionException)
                && failure.getCause() != null) {
            failure = failure.getCause();
        }
        return failure;
    }

    /**
     * 响应违反工具开放策略的专用信号，避免把执行器故障误判为非法模型响应。
     *
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    static final class ToolRoundUnavailableException extends IllegalStateException {
        /** 建立不包含模型内容的固定策略错误。 */
        private ToolRoundUnavailableException() { super("当前模型阶段未开放工具批次。"); }
    }

    /**
     * 无法安全冻结模型响应的协议错误，不持有原始供应商异常。
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    static final class InvalidModelResponseException extends IllegalStateException {
        /** 建立固定协议错误说明，避免导出原始响应内容。 */
        private InvalidModelResponseException() { super("模型响应无法安全冻结。"); }
    }

    /**
     * 本批工具请求的不可变协议快照。
     *
     * @param getId 工具调用 ID
     * @param getType 调用类型
     * @param getName 工具名称
     * @param getArguments 模型业务参数 JSON
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private record Call(String getId, String getType, String getName, String getArguments) implements ILLMToolCall {}
}
