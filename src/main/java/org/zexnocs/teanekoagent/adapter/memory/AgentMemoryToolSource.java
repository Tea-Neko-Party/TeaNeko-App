package org.zexnocs.teanekoagent.adapter.memory;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.agent.AgentActivity;
import org.zexnocs.teanekoagent.agent.AgentDefinition;
import org.zexnocs.teanekoagent.agent.AgentRunRequest;
import org.zexnocs.teanekoagent.agent.IAgentToolSource;
import org.zexnocs.teanekoagent.agent.access.AgentRuntimeSnapshot;
import org.zexnocs.teanekoagent.agent.memory.AgentMemoryReferenceException;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMTool;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMToolBinding;
import org.zexnocs.teanekoagent.llm.framework.tool.ToolInvocationContext;
import org.zexnocs.teanekoagent.llm.framework.tool.interfaces.ILLMFunctionParameter;
import org.zexnocs.teanekoagent.llm.framework.tool.interfaces.ILLMToolCall;
import org.zexnocs.teanekoagent.llm.framework.tool.parameter.AbstractLLMFunctionParameter;
import org.zexnocs.teanekoagent.memory.access.*;
import org.zexnocs.teanekoagent.memory.fact.MemoryEventTime;
import org.zexnocs.teanekoagent.memory.fact.MemoryFactGenerationException;
import org.zexnocs.teanekoagent.memory.fact.MemoryFactReceipt;
import org.zexnocs.teanekoagent.memory.fact.MemoryFactValue;
import org.zexnocs.teanekoagent.memory.recall.*;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * 显式选择当前记忆工具，执行仍经过可信访问服务；不扫描全局工具或提供管理能力。
 * 原始意图在模型调用前由独立原文凭据固定，正文、身份和操作标识均不成为模型业务参数。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public final class AgentMemoryToolSource implements IAgentToolSource {
    public static final String QUERY = "memory_query";
    public static final String PROFILE = "memory_profile";
    public static final String ADD = "memory_add";
    private final MemoryAccessService access;
    private final IMemoryAccessPolicy policies;
    private final IMemoryRecallDefaults defaults;
    private final ObjectMapper mapper;
    private final Executor executor;
    private final MemoryToolReplies replies;

    /**
     * 保存边界和转换依赖，构造不读配置、数据库或创建执行器。
     * @param access 可信记忆访问服务
     * @param policies 当前记忆开关
     * @param defaults 读取用途默认预算
     * @param mapper 项目 JSON 转换器
     * @param executor 借用业务执行器
     * @throws NullPointerException 当依赖为空时
     */
    public AgentMemoryToolSource(MemoryAccessService access, IMemoryAccessPolicy policies, IMemoryRecallDefaults defaults, ObjectMapper mapper, Executor executor) {
        this.access = Objects.requireNonNull(access, "记忆访问服务不能为空。"); this.policies = Objects.requireNonNull(policies, "记忆开关端口不能为空。");
        this.defaults = Objects.requireNonNull(defaults, "记忆读取默认策略不能为空。"); this.executor = Objects.requireNonNull(executor, "记忆工具执行器不能为空。");
        this.mapper = Objects.requireNonNull(mapper, "记忆工具 JSON 转换器不能为空。").rebuild()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
        replies = new MemoryToolReplies(mapper);
    }

    /**
     * 在业务域按已解析引用、真实开关和当前原文选入工具；缺少引用的显式无记忆模式为空。
     * @param definition 当前实例定义
     * @param request 包含可信原文及已解析引用的工具选择请求
     * @param runtime 本轮运行快照，不从中读取凭据或其他权限
     * @return 可见与执行相同的明确工具白名单
     * @throws MemoryAccessException 当当前开关读取不可用时
     */
    @Override public List<LLMToolBinding> select(AgentDefinition definition, AgentRunRequest request, AgentRuntimeSnapshot runtime) {
        if (!definition.activities().contains(AgentActivity.TOOL) || request.memoryReference() == null) { return List.of(); }
        var expected = new MemoryAccessContext(request.requestId(), request.actor(), request.memoryReference());
        final MemoryAccessPolicy policy;
        try { policy = Objects.requireNonNull(policies.read(request.actor().scope()), "记忆工具策略不能为空。"); }
        catch (RuntimeException ignored) { throw new MemoryAccessException(MemoryAccessException.Reason.CONFIGURATION_ERROR); }
        if (!policy.enabled()) { return List.of(); }
        var selected = new ArrayList<LLMToolBinding>();
        if (policy.readEnabled()) {
            var query = budgetProperties(); query.put("subjectId", scalar("string", "可选事实主体的 TeaNeko UUID；省略时查询当前共享空间。"));
            query.put("keywords", array("字面关键词，最多八项，每项不超过 128 字符。", scalar("string", "正文或标签中的字面关键词。")));
            query.put("tags", array("必须全部命中的标签，最多十六项。", scalar("string", "精确标签。")));
            query.put("kinds", array("事实或偏好类型；省略时不限类型。", kind()));
            query.put("start", scalar("string", "可选事件开始边界，带时区的 ISO-8601 时间点。"));
            query.put("end", scalar("string", "可选事件结束边界，带时区的 ISO-8601 时间点。"));
            query.put("includeEstimated", scalar("boolean", "是否允许未知事件使用记录时间估算，默认允许。"));
            selected.add(binding(QUERY, "查询当前共享空间的事实与偏好，保留主体、来源、时间和覆盖限制；部分无命中不表示确定不存在。", query, List.of(), expected, null));
            var profile = budgetProperties(); profile.put("subjectId", scalar("string", "可选 TeaNeko UUID；省略时读取当前真实发起者的画像。"));
            selected.add(binding(PROFILE, "读取指定用户的可追溯事实/偏好画像，结果可能因预算不完整；不推断人格或关系。", profile, List.of(), expected, null));
        }
        var original = request.userText();
        if (policy.addEnabled() && original != null) {
            MemoryAddIntent.fromUserText(expected, original.text()).ifPresent(intent -> selected.add(binding(ADD,
                    "新增本次用户明确要求记住的原文，归属于该用户；可选择事实或偏好，不提供维护能力。",
                    Map.of("kind", kind()), List.of("kind"), expected, intent)));
        }
        return List.copyOf(selected);
    }

    /**
     * 为单轮固定定义、原始上下文和意图；工具参数不能替换闭包中的可信绑定。
     * @param name 固定工具名称
     * @param description 中文能力说明
     * @param properties 模型业务属性
     * @param required 必需业务属性
     * @param expected 原可信上下文
     * @param intent 仅新增工具需要的原始明确意图
     * @return 不公开执行器的冻结绑定
     */
    private LLMToolBinding binding(String name, String description, Map<String, ILLMFunctionParameter> properties, List<String> required,
            MemoryAccessContext expected, @Nullable MemoryAddIntent intent) {
        var tool = LLMTool.builder().name(name).description(description).toolPackage("agent.memory")
                .parameters(AbstractLLMFunctionParameter.object("记忆业务参数，不接受身份、空间或授权声明。", properties, required)).build();
        return new LLMToolBinding(tool, (call, trusted) -> invoke(name, expected, intent, call, trusted));
    }

    /**
     * 验证可信交接后执行业务；原调用 ID 直接来自经过工具集校验的 call，不从 JSON 重建。
     * @param name 固定工具名称
     * @param expected 选择时固定的可信身份
     * @param intent 选择时固定的明确新增意图
     * @param call 工具集已接受的原调用
     * @param trusted 当前阶段上下文与借用控制
     * @return 实际业务完成后的完整有界信封
     */
    private CompletionStage<String> invoke(String name, MemoryAccessContext expected, @Nullable MemoryAddIntent intent,
            ILLMToolCall call, ToolInvocationContext trusted) {
        try {
            trusted.control().checkActive();
            if (!MemoryToolContexts.from(trusted).equals(expected)) { throw new MemoryAccessException(MemoryAccessException.Reason.UNAVAILABLE); }
            var args = mapper.readTree(call.getArguments() == null || call.getArguments().isBlank() ? "{}" : call.getArguments());
            CompletionStage<String> result = switch (name) {
                case QUERY -> access.recall(expected, query(args), MemoryRecallRequest.Use.TOOL, limits(args), trusted.control()).thenApplyAsync(replies::recall, executor);
                case PROFILE -> {
                    var subject = text(args, "subjectId");
                    var id = subject == null ? expected.actor().userId().orElseThrow(() -> new IllegalArgumentException("查询画像时缺少明确主体。")) : UUID.fromString(subject);
                    yield access.profile(expected, id, MemoryRecallRequest.Use.TOOL, limits(args), trusted.control()).thenApplyAsync(replies::profile, executor);
                }
                case ADD -> {
                    if (intent == null) { throw new MemoryAccessException(MemoryAccessException.Reason.INTENT_REQUIRED); }
                    if (call.getId().length() > 128) { yield CompletableFuture.completedStage(replies.error("INVALID_CALL")); }
                    var value = new MemoryFactValue(MemoryFactValue.Kind.valueOf(Objects.requireNonNull(text(args, "kind"), "记忆类别不能为空。")),
                            intent.text(), List.of(), 1, MemoryFactValue.Importance.NORMAL, MemoryEventTime.unknown(), null);
                    yield access.append(expected, call.getId(), intent, value, trusted.control()).thenApplyAsync(receipt ->
                            receipt.status() == MemoryFactReceipt.Status.CONFLICT ? replies.error("OPERATION_CONFLICT") : replies.success(receipt), executor);
                }
                default -> throw new IllegalArgumentException("未声明的记忆工具。");
            };
            return result.exceptionally(this::error);
        } catch (RuntimeException failure) { return CompletableFuture.completedStage(error(failure)); }
    }

    /**
     * 将业务参数转换为原查询合同，不增加 actor/scope 隐式过滤或搜索原始审计 JSON。
     * @param args 已由工具集验证结构的业务参数
     * @return 领域校验后的有界查询
     * @throws IllegalArgumentException 当主体、范围或筛选数量非法时
     * @throws DateTimeException 当时间不是有效的 ISO-8601 时间点时
     */
    private static MemoryRecallQuery query(JsonNode args) {
        var subject = text(args, "subjectId"); var start = text(args, "start"); var end = text(args, "end");
        var estimated = args.get("includeEstimated");
        return new MemoryRecallQuery(subject == null ? null : UUID.fromString(subject), null,
                Set.copyOf(strings(args, "kinds").stream().map(MemoryFactValue.Kind::valueOf).toList()), Set.copyOf(strings(args, "tags")),
                strings(args, "keywords"), start == null ? null : Instant.parse(start), end == null ? null : Instant.parse(end),
                estimated == null || estimated.booleanValue());
    }

    /**
     * 固定本次用途默认条数，并为完整工具信封预留空间；下层还会收紧到自己的最新硬上限。
     * @param args 可选业务预算
     * @return 不超过工具证据预算的领域限制
     * @throws MemoryRecallException 当本地默认策略读取失败时
     */
    private MemoryRecallLimits limits(JsonNode args) {
        final MemoryRecallSettings settings;
        try { settings = Objects.requireNonNull(defaults.settings(), "记忆工具默认预算不能为空。"); }
        catch (RuntimeException ignored) { throw new MemoryRecallException(MemoryRecallException.Reason.CONFIGURATION_ERROR); }
        var explicit = new MemoryRecallLimits(integer(args, "limit", settings.toolLimit()),
                integer(args, "maxCharacters", settings.maxCharacters()), integer(args, "maxScanned", settings.maxScanned()));
        return new MemoryRecallLimits(Math.min(explicit.maxResults(), settings.hardLimit()),
                Math.min(MemoryToolReplies.EVIDENCE_CHARACTERS, Math.min(explicit.maxCharacters(), settings.maxCharacters())),
                Math.min(explicit.maxScanned(), settings.maxScanned()));
    }

    /**
     * 读取可选字符串，不从空值或其他 JSON 类型推断身份。
     * @param args 参数对象
     * @param name 业务字段
     * @return 原字符串或缺失
     */
    private static @Nullable String text(JsonNode args, String name) { var node = args.get(name); return node == null ? null : node.stringValue(); }

    /**
     * 按完整整数校验预算，拒绝溢出或非整数，不进行向下截断。
     * @param args 参数对象
     * @param name 预算字段
     * @param fallback 缺失时默认值
     * @return 原整数或默认值
     * @throws ArithmeticException 当预算不是完整整数或超出整数范围时
     */
    private static int integer(JsonNode args, String name, int fallback) { var node = args.get(name); return node == null ? fallback : new BigDecimal(node.toString()).intValueExact(); }

    /**
     * 复制可选字符串数组，元素类型已由工具集 schema 验证，数量由领域查询继续限制。
     * @param args 参数对象
     * @param name 数组字段
     * @return 独立字符串列表
     */
    private static List<String> strings(JsonNode args, String name) {
        var node = args.get(name); if (node == null) { return List.of(); }
        var values = new ArrayList<String>(); for (var item : node) { values.add(item.stringValue()); } return List.copyOf(values);
    }

    /**
     * 保留固定业务类别，错误信封不包含原文、原始异常链或凭据。
     * @param failure 原阶段错误
     * @return 完整固定错误信封
     */
    private String error(Throwable failure) {
        var current = failure;
        for (int i = 0; i < 32 && (current instanceof CompletionException || current instanceof ExecutionException); i++) {
            var cause = current.getCause(); if (cause == null || cause == current) { break; } current = cause;
        }
        var code = current instanceof MemoryAccessException known ? known.reason().name()
                : current instanceof AgentMemoryReferenceException known ? "REFERENCE_" + known.reason().name()
                : current instanceof MemoryFactGenerationException generation ? generation.missingNamespace() ? "NAMESPACE_MISSING" : "GENERATION_CHANGED"
                : current instanceof MemoryRecallException known ? known.reason().name()
                : current instanceof IllegalArgumentException || current instanceof ArithmeticException || current instanceof DateTimeException ? "INVALID_ARGUMENTS" : "UNAVAILABLE";
        return replies.error(code);
    }

    /**
     * 声明共用预算字段；实际上下限由领域对象和本地策略检查，不依赖模型遵守描述。
     * @return 可供本轮补充业务字段的独立映射
     */
    private static Map<String, ILLMFunctionParameter> budgetProperties() {
        var properties = new LinkedHashMap<String, ILLMFunctionParameter>();
        properties.put("limit", scalar("integer", "可选结果条数，正整数且受本地硬上限约束。"));
        properties.put("maxCharacters", scalar("integer", "可选证据数组字符预算，最小为 2，不能扩大本地及工具上限。"));
        properties.put("maxScanned", scalar("integer", "可选候选检查预算，正整数且不能扩大本地上限。"));
        return properties;
    }

    /**
     * 建立不含执行身份的基础业务 schema。
     * @param type JSON 基础类型
     * @param description 中文业务说明
     * @return 基础属性定义
     */
    private static ILLMFunctionParameter scalar(String type, String description) { return AbstractLLMFunctionParameter.primitive(type, description); }

    /**
     * 声明当前事实类别，拒绝将人格、关系或摘要混入普通新增。
     * @return 仅允许事实和偏好的字符串 schema
     */
    private static ILLMFunctionParameter kind() {
        return AbstractLLMFunctionParameter.builder().type("string").description("FACT 为明确事实，PREFERENCE 为明确偏好。")
                .enumValues(List.of("FACT", "PREFERENCE")).build();
    }

    /**
     * 建立有类型元素的数组 schema，领域校验负责数量与长度边界。
     * @param description 数组业务说明
     * @param items 元素类型
     * @return 数组属性定义
     */
    private static ILLMFunctionParameter array(String description, ILLMFunctionParameter items) {
        return AbstractLLMFunctionParameter.builder().type("array").description(description).items(items).build();
    }
}
