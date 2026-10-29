package org.zexnocs.teanekoagent.llm.framework.interaction;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.llm.framework.LLMOperationRejectedException;
import org.zexnocs.teanekoagent.llm.framework.input.LLMOptionsPolicy;
import org.zexnocs.teanekoagent.llm.framework.input.LLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.input.interfaces.ILLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.message.LLMMessageHistory;
import org.zexnocs.teanekoagent.llm.framework.message.LLMProtocolSnapshots;
import org.zexnocs.teanekoagent.llm.framework.message.content.TextLLMContentPart;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMAssistantMessage;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMMessage;
import org.zexnocs.teanekoagent.llm.framework.model.LLMConfigurationException;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelService;
import org.zexnocs.teanekoagent.llm.framework.response.interfaces.ILLMResult;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMToolSet;
import org.zexnocs.teanekoagent.llm.framework.tool.ToolInvocationContext;
import org.zexnocs.teanekoagent.llm.framework.tool.interfaces.ILLMToolCall;
import org.zexnocs.teanekocore.actuator.execution.BudgetExceededException;
import org.zexnocs.teanekocore.actuator.execution.BudgetKey;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.actuator.execution.ExecutionStoppedException;
import org.zexnocs.teanekocore.api_response.exception.APIRequestTimeoutException;

import java.util.*;
import java.util.concurrent.*;

import static org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionResult.Status.*;

/**
 * 以独立请求状态编排有限模型/工具往返，复用阶段执行、共享预算和固定工具绑定。
 * 不创建线程池，不处理 Agent 人格、记忆、平台发送或业务历史提交。
 *
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 * @see LLMInteractionExecution
 */
public final class LLMInteractionService implements ILLMInteractionService {
    private final ILLMModelService models;
    private final ILLMInteractionDefaults defaults;
    private final Executor executor;
    private final ScheduledExecutorService scheduler;
    private final ILLMTokenEstimator tokenEstimator;

    /**
     * 注入可替换端口与借用执行资源，构造期间不读取本地交互配置。
     *
     * @param models 单次模型端口
     * @param defaults 调用时读取的默认设置端口
     * @param executor 借用的异步业务执行器
     * @param scheduler 借用的截止时间调度器
     */
    public LLMInteractionService(ILLMModelService models, ILLMInteractionDefaults defaults,
                                 Executor executor, ScheduledExecutorService scheduler) {
        this(models, defaults, executor, scheduler, new JsonLLMTokenEstimator(new tools.jackson.databind.ObjectMapper()));
    }

    /**
     * 注入可替换输入估算器，不在构造阶段加载配置或执行模型预检。
     * @param models 单次模型端口
     * @param defaults 默认设置端口
     * @param executor 借用业务执行器
     * @param scheduler 借用计时器
     * @param tokenEstimator 不执行额外供应商请求的估算器
     */
    public LLMInteractionService(ILLMModelService models, ILLMInteractionDefaults defaults,
                                 Executor executor, ScheduledExecutorService scheduler, ILLMTokenEstimator tokenEstimator) {
        this.models = Objects.requireNonNull(models, "模型端口不能为空。");
        this.defaults = Objects.requireNonNull(defaults, "默认设置端口不能为空。");
        this.executor = Objects.requireNonNull(executor, "业务执行器不能为空。");
        this.scheduler = Objects.requireNonNull(scheduler, "计时调度器不能为空。");
        this.tokenEstimator = Objects.requireNonNull(tokenEstimator, "token 估算器不能为空。");
    }

    /**
     * 在任何排队或工具副作用前固定输入，显式覆盖项不触发默认配置读取。
     * 完成阶段不导出原始异常；借用的宿主控制和线程资源不会随交互关闭。
     *
     * @param prompt 初始消息及非敏感模型选项
     * @param tools 本轮固定的可信工具集合
     * @param context 宿主身份与父执行控制
     * @param settings 可选的本轮覆盖设置
     * @return 所有可预期终止路径均以结果完成的只读阶段
     */
    @Override
    public CompletionStage<LLMInteractionResult> interact(ILLMPrompt prompt, LLMToolSet tools,
                                                         ToolInvocationContext context, LLMInteractionSettings settings) {
        return interact(prompt, tools, context, settings, ignored -> {});
    }

    /**
     * 固定宿主守卫并复用有限交互循环，守卫不进入消息、选项或返回快照。
     * @param prompt 初始消息和参数策略
     * @param tools 本轮可信工具集
     * @param context 宿主绑定及父控制
     * @param settings 本轮交互设置
     * @param guard 模型阶段动态准入检查
     * @return 所有已知终止路径均以明确结果完成的阶段
     */
    @Override
    public CompletionStage<LLMInteractionResult> interact(ILLMPrompt prompt, LLMToolSet tools,
            ToolInvocationContext context, LLMInteractionSettings settings, ILLMInteractionGuard guard) {
        final LLMInteractionSettings selected;
        try {
            selected = Objects.requireNonNull(settings == null ? defaults.resolve() : settings, "交互配置不能为空。");
        } catch (RuntimeException failure) {
            return CompletableFuture.completedStage(empty(CONFIGURATION_ERROR));
        }
        final List<ILLMMessage> messages;
        final LLMModelOptions options;
        final LLMOptionsPolicy optionsPolicy;
        final Set<String> ids;
        try {
            Objects.requireNonNull(prompt, "模型输入不能为空。");
            Objects.requireNonNull(tools, "工具集合不能为空。");
            Objects.requireNonNull(context, "宿主上下文不能为空。");
            Objects.requireNonNull(guard, "模型准入守卫不能为空。");
            messages = LLMProtocolSnapshots.messages(prompt.getMessages());
            options = LLMProtocolSnapshots.options(prompt.getOptions());
            optionsPolicy = Objects.requireNonNull(prompt.getOptionsPolicy(), "模型参数解析策略不能为空。");
            ids = validateHistory(messages);
            for (var key : List.of(LLMCallLimits.MODEL_REQUESTS, LLMCallLimits.TOOL_ROUNDS, LLMCallLimits.TOOL_EXECUTIONS)) {
                context.control().budget().remaining(key);
            }
            if (selected.limits().maxTotalTokens() != null) {
                context.control().budget().remaining(LLMCallLimits.TOTAL_TOKENS);
            }
        } catch (RuntimeException failure) {
            return CompletableFuture.completedStage(empty(INVALID_INPUT));
        }
        final Run run;
        try {
            run = new Run(new LLMInteractionExecution(models, tools, context, selected, executor, scheduler, tokenEstimator, guard),
                    messages, options, optionsPolicy, ids);
        } catch (RuntimeException failure) {
            return CompletableFuture.completedStage(empty(classify(failure)));
        }
        return run.next().handle((status, failure) -> {
            try {
                return run.finish(failure == null ? status : run.stopped(failure));
            } finally {
                run.execution.close();
            }
        });
    }

    /**
     * 创建无执行状态的前置失败结果，不把未经验证的输入或异常文本带入结果。
     *
     * @param status 前置失败原因
     * @return 未消耗模型额度的不可变结果
     */
    private static LLMInteractionResult empty(LLMInteractionResult.Status status) {
        return new LLMInteractionResult(status, List.of(), null, List.of(), List.of(), Map.of());
    }

    /**
     * 校验初始历史的工具请求、回填顺序和名称，收集已有 ID 以阻止本次响应重放。
     *
     * @param messages 已冻结的初始历史
     * @return 历史中已经使用过的全部调用 ID
     * @throws IllegalArgumentException 当工具历史不完整、孤立或重复时
     */
    private static Set<String> validateHistory(List<ILLMMessage> messages) {
        return LLMMessageHistory.validateComplete(messages);
    }

    /**
     * 在整批执行前校验工具协议和全链 ID；参数内容由真实工具集继续校验。
     *
     * @param calls 本次模型请求的工具调用
     * @param ids 当前历史已用 ID，成功校验后合并本批 ID
     * @throws IllegalArgumentException 当工具协议非法或调用 ID 重复时
     */
    private static void validateCalls(List<ILLMToolCall> calls, Set<String> ids) {
        LLMMessageHistory.validateCalls(calls, ids);
    }

    /**
     * 按类型分类完成阶段错误，不回显供应商异常、模型输入或业务敏感数据。
     *
     * @param failure 观察到的异常
     * @return 交互终止原因
     */
    private static LLMInteractionResult.Status classify(Throwable failure) {
        if (failure instanceof LLMOperationRejectedException) { return CANCELLED; }
        failure = unwrap(failure);
        if (failure instanceof ExecutionStoppedException stopped) {
            return stopped.reason() == ExecutionControl.StopReason.DEADLINE_EXCEEDED ? TIMED_OUT : CANCELLED;
        }
        if (failure instanceof CancellationException) { return CANCELLED; }
        if (failure instanceof TimeoutException || failure instanceof APIRequestTimeoutException) { return TIMED_OUT; }
        if (failure instanceof BudgetExceededException) { return BUDGET_EXCEEDED; }
        if (failure instanceof LLMConfigurationException) { return CONFIGURATION_ERROR; }
        if (failure instanceof LLMInteractionExecution.ToolRoundUnavailableException) { return INVALID_RESPONSE; }
        if (failure instanceof LLMInteractionExecution.InvalidModelResponseException) { return INVALID_RESPONSE; }
        return FAILED;
    }

    /**
     * 解开完成阶段包装以保留结构化错误类型，不检查或导出异常文本。
     * @param failure 观察到的异常
     * @return 原始业务异常
     */
    private static Throwable unwrap(Throwable failure) {
        while ((failure instanceof CompletionException || failure instanceof ExecutionException)
                && failure.getCause() != null) {
            failure = failure.getCause();
        }
        return failure;
    }

    /**
     * 一次交互私有的串行编排状态；只有当前阶段的完成链可以推进它。
     *
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private static final class Run {
        private final LLMInteractionExecution execution;
        private final List<ILLMMessage> transcript;
        private final LLMModelOptions options;
        private final LLMOptionsPolicy optionsPolicy;
        private final Set<String> ids;
        private final List<LLMInteractionResult.ModelAttempt> attempts = new ArrayList<>();
        private ILLMAssistantMessage candidate;
        private int appendedTools;
        private @Nullable BudgetKey rejectedBudget;

        /**
         * 接管已固定的本轮数据，消息列表转为本轮私有追加容器。
         * @param execution 本轮拥有的阶段执行器
         * @param messages 冻结的初始消息
         * @param options 本轮私有模型选项
         * @param optionsPolicy 本轮固定的默认值解析规则
         * @param ids 初始历史已用的调用 ID
         */
        private Run(LLMInteractionExecution execution, List<ILLMMessage> messages,
                    LLMModelOptions options, LLMOptionsPolicy optionsPolicy, Set<String> ids) {
            this.execution = execution;
            this.transcript = new ArrayList<>(messages);
            this.options = options;
            this.optionsPolicy = optionsPolicy;
            this.ids = ids;
        }

        /**
         * 发起一个模型阶段并登记真实尝试；错误响应不会重试，也不会进入工具副作用路径。
         * @return 当前阶段及所有后续往返的终止状态
         */
        private CompletionStage<LLMInteractionResult.Status> next() {
            long before = execution.usage().get(LLMCallLimits.MODEL_REQUESTS).used();
            return execution.callModel(new LLMPrompt(transcript, options, optionsPolicy)).handle((raw, failure) -> {
                int after = Math.toIntExact(execution.usage().get(LLMCallLimits.MODEL_REQUESTS).used());
                if (failure != null) {
                    if (after > before) {
                        execution.modelAttempt(after).ifPresent(record -> {
                            attempts.add(record);
                            record.response().flatMap(ILLMResult::getFirstMessage).ifPresent(message -> {
                                transcript.add(message);
                                candidate = message.getToolCalls().isEmpty() ? message : null;
                            });
                        });
                    }
                    return CompletableFuture.completedStage(stopped(failure));
                }
                attempts.add(execution.modelAttempt(after).orElseThrow(() -> new IllegalStateException("实际模型尝试缺少完成记录。")));
                return accept(raw);
            }).thenCompose(stage -> stage);
        }

        /**
         * 验证统一响应的终止标识及唯一候选，先完成工具链再请求最终答案。
         * 多候选不能隐式选择一条产生副作用，截断和拒绝也不执行携带的工具。
         * @param response 已冻结的完整供应商响应
         * @return 明确终止状态，或下一次模型阶段
         */
        private CompletionStage<LLMInteractionResult.Status> accept(ILLMResult response) {
            var choices = response.getChoices();
            if (choices.isEmpty()) { return CompletableFuture.completedStage(EMPTY_OUTPUT); }
            if (choices.size() != 1 || choices.getFirst().getIndex() != 0 || choices.getFirst().getMessage() == null) {
                return CompletableFuture.completedStage(INVALID_RESPONSE);
            }
            var choice = choices.getFirst();
            var message = choice.getMessage();
            transcript.add(message);
            var calls = message.getToolCalls();
            candidate = calls.isEmpty() ? message : null;
            String reason = choice.getFinishReason();
            if (Set.of("length", "max_tokens", "max_output_tokens").contains(reason == null ? "" : reason)) {
                return CompletableFuture.completedStage(TRUNCATED);
            }
            if ("content_filter".equals(reason) || "refusal".equals(reason)) {
                return CompletableFuture.completedStage(REFUSED);
            }
            if ("failed".equals(reason) || "error".equals(reason) || "insufficient_system_resource".equals(reason)) {
                return CompletableFuture.completedStage(FAILED);
            }
            if ("cancelled".equals(reason)) { return CompletableFuture.completedStage(CANCELLED); }
            if (calls.isEmpty()) {
                if (!"stop".equals(reason)) { return CompletableFuture.completedStage(INVALID_RESPONSE); }
                boolean visible = message.getContents().stream().anyMatch(content ->
                        !(content.getContentPart() instanceof TextLLMContentPart text) || !text.getText().isBlank());
                return CompletableFuture.completedStage(visible ? SUCCESS : EMPTY_OUTPUT);
            }
            if (!"tool_calls".equals(reason)) { return CompletableFuture.completedStage(INVALID_RESPONSE); }
            try {
                validateCalls(calls, ids);
            } catch (RuntimeException invalid) {
                return CompletableFuture.completedStage(INVALID_RESPONSE);
            }
            return execution.executeTools(calls).handle((results, failure) -> {
                appendTools();
                if (failure != null) {
                    return CompletableFuture.completedStage(stopped(failure));
                }
                return next();
            }).thenCompose(stage -> stage);
        }

        /**
         * 追加阶段执行器已观察到完成的工具，保留中途终止前已有结果及调用顺序。
         */
        private void appendTools() {
            var completed = execution.toolResults();
            for (; appendedTools < completed.size(); appendedTools++) {
                transcript.add(LLMProtocolSnapshots.message(completed.get(appendedTools).toMessage()));
            }
        }

        /**
         * 保存导致终止的预算维度，再进行统一异常分类，避免调用方解析异常字符串。
         * @param failure 当前阶段失败
         * @return 可观察终止状态
         */
        private LLMInteractionResult.Status stopped(Throwable failure) {
            var cause = unwrap(failure);
            if (cause instanceof BudgetExceededException exceeded) { rejectedBudget = exceeded.key(); }
            return classify(cause);
        }

        /**
         * 汇集完整诊断链与用量；实际业务历史是否提交仍由调用方决定。
         * @param status 已收束的终止状态
         * @return 不持有本轮运行状态的完整结果
         */
        private LLMInteractionResult finish(LLMInteractionResult.Status status) {
            appendTools();
            return new LLMInteractionResult(status, transcript, Optional.ofNullable(candidate), attempts,
                    execution.toolResults(), execution.usage(), Optional.ofNullable(rejectedBudget));
        }
    }
}
