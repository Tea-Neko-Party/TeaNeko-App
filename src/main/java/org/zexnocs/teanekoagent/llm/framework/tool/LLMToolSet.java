package org.zexnocs.teanekoagent.llm.framework.tool;

import org.zexnocs.teanekoagent.llm.framework.LLMOperationRejectedException;
import org.zexnocs.teanekoagent.llm.framework.tool.interfaces.ILLMFunctionParameter;
import org.zexnocs.teanekoagent.llm.framework.tool.interfaces.ILLMTool;
import org.zexnocs.teanekoagent.llm.framework.tool.interfaces.ILLMToolCall;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.actuator.execution.ExecutionStoppedException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;

import static org.zexnocs.teanekoagent.llm.framework.tool.ToolInvocationResult.Status;

/**
 * 一轮固定的可见且可执行工具集。没有全局工具名回退，不授予业务权限。
 * 本入口等待真实完成并响应 control；阶段计时、次数预算和重放记录由交互/业务层负责。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public final class LLMToolSet {
    public static final int DEFAULT_RESULT_CHARACTER_LIMIT = 8000;
    private final Map<String, LLMToolBinding> bindings;
    private final List<ILLMTool> definitions;
    private final ObjectMapper objectMapper;
    private final int resultCharacterLimit;

    /**
     * 建立本轮可见且可执行的同一工具集合，保留选择顺序，固定结果上限并拒绝同名绑定。
     *
     * @param objectMapper 项目 JSON 解析与转换器
     * @param bindings 宿主固定的不可变业务绑定或本轮工具绑定集合
     */
    public LLMToolSet(ObjectMapper objectMapper, Collection<LLMToolBinding> bindings) {
        this(objectMapper, bindings, DEFAULT_RESULT_CHARACTER_LIMIT);
    }

    /**
     * 建立本轮可见且可执行的同一工具集合，保留选择顺序，固定结果上限并拒绝同名绑定。
     *
     * @param objectMapper 项目 JSON 解析与转换器
     * @param bindings 宿主固定的不可变业务绑定或本轮工具绑定集合
     * @param resultCharacterLimit 单次结果最大字符数，至少保留完整错误信封
     * @throws IllegalArgumentException 当参数格式、范围或声明的类型约束不满足时
     * @throws NullPointerException 当必需的输入或依赖为空时
     */
    public LLMToolSet(ObjectMapper objectMapper, Collection<LLMToolBinding> bindings, int resultCharacterLimit) {
        // 错误回填也须为完整 JSON，至少保留一个固定错误信封的空间。
        if (resultCharacterLimit < 128) {
            throw new IllegalArgumentException("Tool result character limit must be at least 128.");
        }
        this.resultCharacterLimit = resultCharacterLimit;
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper").rebuild()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .build();
        var selected = new LinkedHashMap<String, LLMToolBinding>();
        for (var binding : bindings) {
            Objects.requireNonNull(binding, "binding");
            if (selected.putIfAbsent(binding.definition().getName(), binding) != null) {
                throw new IllegalArgumentException("Duplicate selected tool name.");
            }
        }
        this.bindings = Collections.unmodifiableMap(selected);
        this.definitions = selected.values().stream().map(LLMToolBinding::definition).toList();
    }

    /**
     * 提供本轮选入工具的有序不可变定义，与执行时使用的白名单保持一致。
     *
     * @return 与执行白名单一致的有序不可变工具定义
     */
    public List<ILLMTool> definitions() {
        return definitions;
    }

    /**
     * 验证调用与参数后等待固定执行器完成；响应控制终止，保留调用快照并脱敏回填错误，不自动重试。
     *
     * @param source 待处理的来源数据
     * @param context 用于本次处理的 {@code context} 数据
     * @return 在工具真实完成或控制终止后形成的回填结果阶段
     * @throws NullPointerException 当必需的输入或依赖为空时
     */
    public CompletionStage<ToolInvocationResult> execute(ILLMToolCall source, ToolInvocationContext context) {
        Objects.requireNonNull(source, "call");
        Objects.requireNonNull(context, "context");
        var call = new Call(source.getId(), source.getType(), source.getName(), source.getArguments());
        try {
            context.control().checkActive();
            if (!"function".equals(call.getType()) || call.getId() == null || call.getId().isBlank()
                    || call.getName() == null || call.getName().isBlank()) {
                return completed(error(call, Status.INVALID_CALL));
            }
            var binding = bindings.get(call.getName());
            if (binding == null) {
                return completed(error(call, Status.NOT_ALLOWED));
            }
            if (!validArguments(call.getArguments(), binding.definition().getParameters())) {
                return completed(error(call, Status.INVALID_ARGUMENTS));
            }
            context.control().checkActive();
            var stage = Objects.requireNonNull(binding.executor().call(call, context), "Tool returned a null Stage.");
            return context.control().guard(stage).handle((output, failure) -> {
                if (failure != null) {
                    var cause = unwrap(failure);
                    if (cause instanceof LLMOperationRejectedException rejected) { throw new CompletionException(rejected); }
                    return error(call, status(failure));
                }
                if (output == null) {
                    return error(call, Status.FAILED);
                }
                var end = Math.min(output.length(), resultCharacterLimit);
                if (end < output.length() && end > 0 && Character.isHighSurrogate(output.charAt(end - 1))
                        && Character.isLowSurrogate(output.charAt(end))) {
                    end--;
                }
                return new ToolInvocationResult(call, Status.SUCCESS, output.substring(0, end), end < output.length());
            });
        } catch (Exception failure) {
            var cause = unwrap(failure);
            if (cause instanceof LLMOperationRejectedException) { return CompletableFuture.failedStage(cause); }
            try {
                context.control().checkActive();
            } catch (ExecutionStoppedException stopped) {
                return completed(error(call, status(stopped)));
            }
            return completed(error(call, status(failure)));
        }
    }

    /**
     * 严格解析有界 JSON 对象，拒绝重复键、尾随内容和不符合本轮 schema 的参数。
     *
     * @param arguments 模型提供的业务参数 JSON
     * @param schema 本轮固定的参数 schema
     * @return 参数是合法且符合当前 schema 的有界 JSON 对象时为 true
     */
    private boolean validArguments(String arguments, ILLMFunctionParameter schema) {
        try {
            if (arguments != null && arguments.length() > 65536) {
                return false;
            }
            Object value = arguments == null || arguments.isBlank() ? Map.of()
                    : objectMapper.readValue(arguments, Object.class);
            return validValue(value, schema, 0);
        } catch (RuntimeException invalid) {
            return false; // 解析错误可能包含原始参数，不回填其消息或 cause。
        }
    }

    /**
     * 递归检查类型、必需字段、枚举和额外属性策略，限制嵌套深度，避免隐式类型转换。
     *
     * @param value 待处理的值
     * @param schema 本轮固定的参数 schema
     * @param depth 当前嵌套深度
     * @return 值满足声明的类型、深度和字段约束时为 true
     */
    private boolean validValue(Object value, ILLMFunctionParameter schema, int depth) {
        if (value == null || depth > 64) {
            return false;
        }
        return switch (schema.getType()) {
            case "object" -> {
                if (!(value instanceof Map<?, ?> map)) {
                    yield false;
                }
                var missing = new HashSet<>(schema.getRequired());
                boolean valid = true;
                for (var entry : map.entrySet()) {
                    if (!(entry.getKey() instanceof String name)) { yield false; }
                    missing.remove(name);
                    var property = schema.getProperties().get(name);
                    if (property == null ? !schema.isAdditionalProperties()
                            : !validValue(entry.getValue(), property, depth + 1)) {
                        valid = false;
                        break;
                    }
                }
                yield valid && missing.isEmpty();
            }
            case "array" -> value instanceof List<?> list && (schema.getItems() == null
                    || list.stream().allMatch(item -> validValue(item, schema.getItems(), depth + 1)));
            case "string" -> value instanceof String string && (schema.getEnumValues().isEmpty()
                    || schema.getEnumValues().contains(string));
            case "boolean" -> value instanceof Boolean;
            case "number" -> value instanceof Number;
            case "integer" -> value instanceof Number number
                    && new BigDecimal(number.toString()).stripTrailingZeros().scale() <= 0;
            default -> false;
        };
    }

    /**
     * 解包完成阶段异常并区分超时、取消与普通失败，不将原始异常文本暴露给模型。
     *
     * @param failure 需要传播或分类的异常
     * @return 不携带原始异常内容的结构化终止状态
     */
    private static Status status(Throwable failure) {
        failure = unwrap(failure);
        if (failure instanceof ExecutionStoppedException stopped) {
            return stopped.reason() == ExecutionControl.StopReason.DEADLINE_EXCEEDED
                    ? Status.TIMED_OUT : Status.CANCELLED;
        }
        if (failure instanceof CancellationException) {
            return Status.CANCELLED;
        }
        return failure instanceof TimeoutException ? Status.TIMED_OUT : Status.FAILED;
    }

    /**
     * 解开完成阶段包装，以便保留宿主拒绝与普通工具故障的不同语义。
     * @param failure 观察到的异常
     * @return 不含完成包装的原始异常
     */
    private static Throwable unwrap(Throwable failure) {
        while ((failure instanceof CompletionException || failure instanceof ExecutionException)
                && failure.getCause() != null) {
            failure = failure.getCause();
        }
        return failure;
    }

    /**
     * 构造固定 JSON 错误信封，保留调用配对而不复制参数、堆栈或可信绑定。
     *
     * @param call 模型产生的工具调用，身份不从参数中取得
     * @param status 本次处理的终止状态
     * @return 保留原调用配对信息且不携带敏感异常的回填结果
     */
    private static ToolInvocationResult error(ILLMToolCall call, Status status) {
        return new ToolInvocationResult(call, status,
                "{\"ok\":false,\"error\":{\"code\":\"" + status.name() + "\"}}", false);
    }

    /**
     * 创建只读的已完成结果阶段，供未启动工具的拒绝分支统一结束等待。
     *
     * @param result 已经形成的处理结果
     * @return 在工具真实完成或控制终止后形成的回填结果阶段
     */
    private static CompletionStage<ToolInvocationResult> completed(ToolInvocationResult result) {
        return CompletableFuture.completedFuture(result).minimalCompletionStage();
    }

    /**
     * 工具调用的不可变快照，防止原 DTO 修改破坏迟到结果的 ID 配对。
     *
     * @param getId 原工具调用 ID
     * @param getType 原工具调用类型
     * @param getName 原工具调用名称
     * @param getArguments 模型提供的原始参数 JSON
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private record Call(String getId, String getType, String getName, String getArguments) implements ILLMToolCall {}
}
