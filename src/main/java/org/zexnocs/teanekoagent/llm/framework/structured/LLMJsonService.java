package org.zexnocs.teanekoagent.llm.framework.structured;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.llm.framework.input.LLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.input.interfaces.ILLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.interaction.ILLMInteractionGuard;
import org.zexnocs.teanekoagent.llm.framework.interaction.ILLMInteractionService;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMCallLimits;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionResult;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionSettings;
import org.zexnocs.teanekoagent.llm.framework.message.LLMSystemMessage;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.message.content.TextLLMContentPart;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMMessage;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelService;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.LLMResponseFormat;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMToolSet;
import org.zexnocs.teanekoagent.llm.framework.tool.ToolInvocationContext;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

/**
 * 按已登记 Java 类型进行结构化生成，复用原交互预算和动态守卫；纠错由独立入口显式启用。
 * <br>2026/11/06：统一结构提示、原生 JSON 选项和严格解码，不隐含重试或提交业务状态。
 * <br>2026/11/06：按原生能力去除重复结构提示，保持输入预检、观察与实际发送一致。
 * <br>2026/11/07：迁入独立 LLM 子模块，保留公开协议与原配置语义。
 * <br>2026/11/10：短指令保留 Schema 顶层字段名，减少兼容网关忽略原生约束时的别名输出。
 * <br>2026/11/10：显式纠错入口反馈结构错误及完整合同，最多追加一次且共享原预算、总时限与准入。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.3-l
 * @version 4.9.1-l
 */
@Service
public final class LLMJsonService {
    private final LLMJsonSchemas schemas;
    private final ILLMInteractionService interactions;
    private final ObjectMapper mapper;
    private final LLMJsonCodec codec;
    private final LLMJsonDiagnostics diagnostics;
    private final ILLMModelService models;
    // 2026/11/10：只借用原调度器，为首次生成和纠错建立共同截止时间。
    private final ScheduledExecutorService scheduler;

    /**
     * @param schemas 初始化完成的类型目录
     * @param interactions 借用的交互服务
     * @param mapper 应用转换器
     * @param diagnostics 受限本机诊断
     * @param models 原模型服务，用于固定实际结构协议而不发起请求
     * @param scheduler 借用截止调度器，不由本服务关闭
     */
    public LLMJsonService(LLMJsonSchemas schemas, ILLMInteractionService interactions, ObjectMapper mapper,
            LLMJsonDiagnostics diagnostics, ILLMModelService models,
            @Qualifier("taskExecutor") ScheduledExecutorService scheduler) {
        this.schemas = schemas;
        this.interactions = interactions;
        this.mapper = mapper;
        this.codec = new LLMJsonCodec(mapper);
        this.diagnostics = diagnostics;
        this.models = models;
        this.scheduler = scheduler;
    }

    /**
     * 简易生成入口，返回完整目标对象，失败以安全分类异步传播。
     * @param type 标记了 {@link LLMJsonType} 的类型
     * @param prompt 业务输入，服务追加固定结构说明
     * @param context 原身份、预算与取消控制
     * @param settings 原交互限制
     * @param guard 每个模型阶段的动态准入
     * @param <T> 输出类型
     * @return 严格绑定结果，不包含持久化行为
     */
    public <T> CompletionStage<T> generate(Class<T> type, ILLMPrompt prompt, ToolInvocationContext context,
            LLMInteractionSettings settings, ILLMInteractionGuard guard) {
        return generate(prepare(type, prompt), context, settings, guard, ignored -> { });
    }

    /**
     * 2026/11/10：供拥有图片资源及备用请求的调用者使用，解码前等待真实模型阶段结束。
     * @param type 已登记结果类型
     * @param prompt 原输入
     * @param context 原身份和控制
     * @param settings 每次生成限制
     * @param guard 动态准入
     * @param <T> 结果类型
     * @return 已排空的严格结果，失败不会提前释放资源
     */
    public <T> CompletionStage<T> generateSettled(Class<T> type, ILLMPrompt prompt, ToolInvocationContext context,
            LLMInteractionSettings settings, ILLMInteractionGuard guard) {
        var request = prepare(type, prompt);
        context.control().checkActive();
        return decode(request, context, ignored -> { }, interactions.interactSettled(request.prompt(),
                new LLMToolSet(mapper, List.of()), context, settings, guard));
    }

    /**
     * 在本地输入预检前固定完整合同；返回的提示必须用于预算估算和真实调用。
     * @param type 已登记类型
     * @param source 原提示
     * @param <T> 输出类型
     * @return 包含完整结构说明和选项的请求
     */
    public <T> Request<T> prepare(Class<T> type, ILLMPrompt source) {
        var definition = schemas.require(type);
        var options = LLMModelOptions.copyOf(source.getOptions());
        options.setResponseFormat(LLMResponseFormat.JSON);
        options.setResponseSchema(definition.schema());
        options.setTools(List.of());
        options.setToolChoice("none");
        var messages = new ArrayList<ILLMMessage>();
        messages.add(instruction(definition));
        messages.addAll(source.getMessages());
        // 2026/11/06：业务的前置字节预算也使用实际协议，不能再次计算已移除的全文 Schema。
        var actual = models.prepareRequest(new LLMPrompt(List.copyOf(messages), options, source.getOptionsPolicy()));
        return new Request<>(type, definition,
                new LLMPrompt(actual.getMessages(), actual.getOptions(), actual.getOptionsPolicy()));
    }

    /**
     * 对已有宿主流程提供同一结构说明，不要求绕开 Agent 管道。
     * @param type 已登记类型
     * @return JSON 选项及提示共用的固定声明
     */
    public LLMJsonSchemas.Definition definition(Class<?> type) { return schemas.require(type); }

    /**
     * 生成结构描述；描述含 @Description 文本，不能用不可信数据替换。
     * @param definition 固定声明
     * @return 仅输出一个 JSON 对象的系统约束
     */
    public LLMJsonInstruction instruction(LLMJsonSchemas.Definition definition) {
        // 2026/11/10：字段名直接取自已编译 Schema，原生模式也提供短合同，不重复嵌套全文。
        var fields = mapper.valueToTree(definition.schema().schema()).path("properties").propertyNames();
        return new LLMJsonInstruction(
                "只输出一个完整 JSON 对象，遵循以下 JSON Schema。每个字段都必须出现；可空字段显式写 null。"
                + "顶层字段名必须原样使用：" + mapper.writeValueAsString(fields) + "，不得替换为别名。"
                + "不使用 Markdown 代码围栏、解释、前后缀或额外字段。输出最多 " + definition.maxCharacters()
                + " 字符。字段描述和业务约束同样必须遵守。",
                mapper.writeValueAsString(definition.schema().schema()), false);
    }

    /**
     * 以固定请求调用原交互服务；观察者在解码前取得真实状态及用量。
     * @param request 预检过的完整请求
     * @param context 原控制
     * @param settings 原限制
     * @param guard 动态准入
     * @param observer 用量观察者，不拥有执行资源
     * @param <T> 输出类型
     * @return 严格解码的对象
     */
    public <T> CompletionStage<T> generate(Request<T> request, ToolInvocationContext context,
            LLMInteractionSettings settings, ILLMInteractionGuard guard, Consumer<LLMInteractionResult> observer) {
        context.control().checkActive();
        return decode(request, context, observer, interactions.interact(request.prompt(),
                new LLMToolSet(mapper, List.of()), context, settings, guard));
    }

    /**
     * 2026/11/10：显式允许一次格式纠错；成功响应的结构错误才可重试，不改变业务数据或证据。
     * 首次和纠错共用总截止时间、父预算及身份；每次输入重新预检、真实调用前仍复核动态准入。
     * @param request 固定输入及结构合同
     * @param context 原身份和父执行控制
     * @param settings 两次生成合计的调用和 token 限额，以及共同总时限
     * @param guard 每次模型阶段的动态准入
     * @param observer 每次交互实际结束后的用量观察者
     * @param inputCheck 每次完整输入的业务容量检查，不修改请求
     * @param <T> 输出类型
     * @return 等待真实调用结束后的严格结果；持续错误仍异常结束
     */
    public <T> CompletionStage<T> generateWithCorrection(Request<T> request, ToolInvocationContext context,
            LLMInteractionSettings settings, ILLMInteractionGuard guard, Consumer<LLMInteractionResult> observer,
            Consumer<ILLMPrompt> inputCheck) {
        var control = context.control().child(settings.turnTimeout(), scheduler);
        try {
            return correctedAttempt(request, context.withControl(control), settings, guard, observer, inputCheck, true)
                    .whenComplete((ignored, failure) -> control.close());
        } catch (RuntimeException failure) {
            control.close();
            throw failure;
        }
    }

    /**
     * 2026/11/10：只捕获严格解码的安全错误；观察者、权限及传输故障不得被误作可修正的输出。
     * @param request 当前完整请求
     * @param context 共享总时限与父预算的上下文
     * @param settings 剩余局部预算和原阶段时限
     * @param guard 原准入
     * @param observer 真实交互观察者
     * @param inputCheck 完整输入预检
     * @param allowCorrection 是否尚未使用唯一一次纠错
     * @param <T> 结果类型
     * @return 严格结果或保留真实原因的失败阶段
     */
    private <T> CompletionStage<T> correctedAttempt(Request<T> request, ToolInvocationContext context,
            LLMInteractionSettings settings, ILLMInteractionGuard guard, Consumer<LLMInteractionResult> observer,
            Consumer<ILLMPrompt> inputCheck, boolean allowCorrection) {
        context.control().checkActive();
        inputCheck.accept(request.prompt());
        return interactions.interactSettled(request.prompt(), new LLMToolSet(mapper, List.of()), context, settings, guard)
                .thenCompose(result -> {
                    observer.accept(result);
                    context.control().checkActive();
                    try {
                        return CompletableFuture.completedStage(decodeResult(request, context.requestId(), result));
                    } catch (LLMJsonException failure) {
                        boolean correctable = failure.status() == LLMInteractionResult.Status.SUCCESS
                                && switch (failure.code()) {
                                    case JSON_SYNTAX, JSON_SHAPE, JSON_BINDING, OUTPUT_LIMIT -> true;
                                    default -> false;
                                };
                        if (!allowCorrection || !correctable || result.modelAttempts() >= settings.limits().maxModelCalls()
                                || context.control().budget().remaining(LLMCallLimits.MODEL_REQUESTS) < 1) {
                            return CompletableFuture.failedStage(failure);
                        }
                        var usage = Objects.requireNonNull(result.budgetUsage().get(LLMCallLimits.MODEL_REQUESTS),
                                "结构化纠错必须取得原交互的模型调用用量。");
                        Long tokens = settings.limits().maxTotalTokens();
                        if (tokens != null) {
                            var spent = Objects.requireNonNull(result.budgetUsage().get(LLMCallLimits.TOTAL_TOKENS),
                                    "结构化纠错必须取得已启用的局部 token 用量。");
                            if (spent.exceeded() || spent.used() >= tokens) {
                                return CompletableFuture.failedStage(failure);
                            }
                            tokens -= spent.used();
                        }
                        var remaining = new LLMInteractionSettings(new LLMCallLimits(
                                Math.toIntExact(settings.limits().maxModelCalls() - usage.used()), 0, 0, tokens),
                                settings.modelTimeout(), settings.toolTimeout(), settings.turnTimeout());
                        return correctedAttempt(correction(request, failure), context, remaining, guard, observer,
                                inputCheck, false);
                    }
                });
    }

    /**
     * 2026/11/10：纠错显式重发完整 Schema，兼容忽略原生结构参数的服务；不回填不可信原答案。
     * @param request 原业务资料与固定合同
     * @param failure 已脱敏的校验报告
     * @param <T> 输出类型
     * @return 追加一次反馈的独立请求，不修改来源消息和参数
     */
    private <T> Request<T> correction(Request<T> request, LLMJsonException failure) {
        var messages = new ArrayList<>(request.prompt().getMessages());
        messages.add(LLMSystemMessage.builder().contents(new LLMContentListBuilder().addText(
                "上一次输出未通过结构校验，请根据原始资料重新生成完整 JSON 对象。校验报告："
                        + failure.failureReason()
                        + "\n必须逐项遵守下方完整 JSON Schema，所有必需字段和嵌套字段都要出现，不使用别名或额外字段。"
                        + "继续遵守原业务证据约束，不为补齐结构而编造事实，也不为通过校验而删除有依据的内容。"
                        + "不要输出解释、Markdown 或差异补丁。本次完整 JSON Schema："
                        + mapper.writeValueAsString(request.definition().schema().schema())).build()).build());
        var prompt = models.prepareRequest(new LLMPrompt(List.copyOf(messages),
                LLMModelOptions.copyOf(request.prompt().getOptions()), request.prompt().getOptionsPolicy()));
        return new Request<>(request.type(), request.definition(),
                new LLMPrompt(prompt.getMessages(), prompt.getOptions(), prompt.getOptionsPolicy()));
    }

    /**
     * @param request 固定结构合同
     * @param context 原控制
     * @param observer 原结果观察者
     * @param pending 具有明确完成语义的交互结果
     * @param <T> 结果类型
     * @return 严格解码结果
     */
    private <T> CompletionStage<T> decode(Request<T> request, ToolInvocationContext context,
            Consumer<LLMInteractionResult> observer, CompletionStage<LLMInteractionResult> pending) {
        return pending.thenApply(result -> {
            // 2026/11/10：单次与纠错共用严格解码，观察和执行资格在解码前复核。
            observer.accept(result);
            context.control().checkActive();
            return decodeResult(request, context.requestId(), result);
        });
    }

    /**
     * 2026/11/10：仅解释实际交互结果，不自行请求模型或放宽本地合同。
     * @param request 固定目标类型和 Schema
     * @param requestId 原请求关联编号
     * @param result 实际模型交互结果
     * @param <T> 结果类型
     * @return 严格绑定的完整对象
     * @throws LLMJsonException 当模型终态或输出不满足结构合同时
     */
    private <T> T decodeResult(Request<T> request, UUID requestId, LLMInteractionResult result) {
        if (result.status() != LLMInteractionResult.Status.SUCCESS) {
            throw new LLMJsonException(LLMJsonException.Code.MODEL_RESULT, result.status(), requestId,
                    result.failure() == null ? statusDescription(result.status()) : result.failure().description(),
                    result.failure());
        }
        var message = result.finalCandidate().orElseThrow(() -> new LLMJsonException(
                LLMJsonException.Code.MISSING_OUTPUT, result.status(), requestId, "模型未返回最终消息。", null));
        var text = new StringBuilder();
        for (var content : message.getContents()) {
            if (!(content.getContentPart() instanceof TextLLMContentPart part)) {
                throw new LLMJsonException(LLMJsonException.Code.JSON_SHAPE, result.status(), requestId,
                        "JSON 结果包含非文本内容。", null);
            }
            text.append(part.getText());
        }
        return read(request.type(), request.definition(), text.toString(), requestId);
    }

    /**
     * 为仍须经过 Agent 管道或兼容协议的结果使用同一严格解码入口。
     * @param type 已登记类型
     * @param text 已确认成功的最终正文
     * @param request 原请求号
     * @param <T> 目标类型
     * @return 完整结果
     */
    public <T> T read(Class<T> type, String text, UUID request) {
        return read(type, schemas.require(type), text, request);
    }

    /**
     * 使用生成时固定的 Schema 解码，重扫不能改变已在执行的合同。
     * @param type 固定类型
     * @param definition 原声明
     * @param text 最终文本
     * @param request 原请求号
     * @param <T> 输出类型
     * @return 完整结果
     */
    private <T> T read(Class<T> type, LLMJsonSchemas.Definition definition, String text, UUID request) {
        try {
            return codec.read(type, definition, text, request);
        } catch (LLMJsonException failure) {
            diagnostics.record(request, text);
            throw failure;
        }
    }

    /**
     * 缺少 HTTP 原因时只解释实际状态，不虚构服务端错误。
     * @param status 实际终态
     * @return 可公开的排查方向
     */
    private static String statusDescription(LLMInteractionResult.Status status) {
        return switch (status) {
            case TRUNCATED -> "模型输出被截断，请检查 max-tokens、上下文容量和推理消耗。";
            case REFUSED -> "模型拒绝了本次生成。";
            case EMPTY_OUTPUT -> "模型未返回可用正文。";
            case TIMED_OUT -> "模型请求超过执行时限。";
            case CANCELLED -> "模型请求已取消。";
            case BUDGET_EXCEEDED -> "模型调用或 token 预算不足。";
            case CONFIGURATION_ERROR -> "模型路由、参数或能力配置不可用。";
            case INVALID_INPUT -> "模型输入或宿主上下文校验失败。";
            case INVALID_RESPONSE -> "供应商响应不符合协议。";
            case FAILED -> "模型交互失败，未取得 HTTP 状态或更具体诊断。";
            case SUCCESS -> "模型已完成。";
        };
    }

    /**
     * 固定生成与解码共用的合同，不包含业务持久化状态。
     * @param type 输出类型
     * @param definition 原结构及容量
     * @param prompt 完整提示
     * @param <T> 输出类型
     * @author zExNocs
     * @date 2026/11/06
     * @since 4.8.3-l
     * @version 4.8.3-l
     */
    public record Request<T>(Class<T> type, LLMJsonSchemas.Definition definition, LLMPrompt prompt) { }
}
