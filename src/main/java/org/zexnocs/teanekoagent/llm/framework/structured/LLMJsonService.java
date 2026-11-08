package org.zexnocs.teanekoagent.llm.framework.structured;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.llm.framework.input.LLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.input.interfaces.ILLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.interaction.ILLMInteractionGuard;
import org.zexnocs.teanekoagent.llm.framework.interaction.ILLMInteractionService;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionResult;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionSettings;
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
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/**
 * 按已登记 Java 类型进行一次结构化生成，复用原交互预算和动态守卫。
 * <br>2026/11/06：统一结构提示、原生 JSON 选项和严格解码，不隐含重试或提交业务状态。
 * <br>2026/11/06：按原生能力去除重复结构提示，保持输入预检、观察与实际发送一致。
 * <br>2026/11/07：迁入独立 LLM 子模块，保留公开协议与原配置语义。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.3-l
 * @version 4.9.0-l
 */
@Service
public final class LLMJsonService {
    private final LLMJsonSchemas schemas;
    private final ILLMInteractionService interactions;
    private final ObjectMapper mapper;
    private final LLMJsonCodec codec;
    private final LLMJsonDiagnostics diagnostics;
    private final ILLMModelService models;

    /**
     * @param schemas 初始化完成的类型目录
     * @param interactions 借用的交互服务
     * @param mapper 应用转换器
     * @param diagnostics 受限本机诊断
     * @param models 原模型服务，用于固定实际结构协议而不发起请求
     */
    public LLMJsonService(LLMJsonSchemas schemas, ILLMInteractionService interactions, ObjectMapper mapper,
            LLMJsonDiagnostics diagnostics, ILLMModelService models) {
        this.schemas = schemas;
        this.interactions = interactions;
        this.mapper = mapper;
        this.codec = new LLMJsonCodec(mapper);
        this.diagnostics = diagnostics;
        this.models = models;
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
        // 2026/11/06：保留类型化结构来源，实际适配时避免原生 Schema 与全文提示重复。
        return new LLMJsonInstruction(
                "只输出一个完整 JSON 对象，遵循以下 JSON Schema。每个字段都必须出现；可空字段显式写 null。"
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
        return interactions.interact(request.prompt(), new LLMToolSet(mapper, List.of()), context, settings, guard)
                .thenApply(result -> {
                    observer.accept(result);
                    context.control().checkActive();
                    if (result.status() != LLMInteractionResult.Status.SUCCESS) {
                        throw new LLMJsonException(LLMJsonException.Code.MODEL_RESULT, result.status(), context.requestId(),
                                result.failure() == null ? statusDescription(result.status()) : result.failure().description(),
                                result.failure());
                    }
                    var message = result.finalCandidate().orElseThrow(() -> new LLMJsonException(
                            LLMJsonException.Code.MISSING_OUTPUT, result.status(), context.requestId(), "模型未返回最终消息。", null));
                    var text = new StringBuilder();
                    for (var content : message.getContents()) {
                        if (!(content.getContentPart() instanceof TextLLMContentPart part)) {
                            throw new LLMJsonException(LLMJsonException.Code.JSON_SHAPE, result.status(), context.requestId(),
                                    "JSON 结果包含非文本内容。", null);
                        }
                        text.append(part.getText());
                    }
                    return read(request.type(), request.definition(), text.toString(), context.requestId());
                });
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
