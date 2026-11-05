package org.zexnocs.teanekoagent.component.memory.adapter;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.component.llm.framework.tool.LLMToolBinding;
import org.zexnocs.teanekoagent.component.llm.framework.tool.ToolInvocationContext;
import org.zexnocs.teanekoagent.component.llm.framework.tool.interfaces.ILLMToolCall;
import org.zexnocs.teanekoagent.component.memory.access.*;
import org.zexnocs.teanekoagent.component.memory.fact.MemoryEventTime;
import org.zexnocs.teanekoagent.component.memory.fact.MemoryFactGenerationException;
import org.zexnocs.teanekoagent.component.memory.fact.MemoryFactReceipt;
import org.zexnocs.teanekoagent.component.memory.fact.MemoryFactValue;
import org.zexnocs.teanekoagent.component.memory.recall.*;
import org.zexnocs.teanekoagent.component.memory.runtime.AgentMemoryReferenceException;
import org.zexnocs.teanekoagent.component.tool.TeaNekoAgentToolContext;
import org.zexnocs.teanekoagent.component.tool.TeaNekoAgentToolMethods;
import org.zexnocs.teanekoagent.component.tool.api.TeaNekoAgentTool;
import org.zexnocs.teanekoagent.component.tool.api.TeaNekoAgentToolClass;
import org.zexnocs.teanekoagent.component.tool.api.TeaNekoAgentToolParam;
import org.zexnocs.teanekoagent.runtime.AgentDefinition;
import org.zexnocs.teanekoagent.runtime.AgentRunRequest;
import org.zexnocs.teanekoagent.runtime.IAgentToolSource;
import org.zexnocs.teanekoagent.runtime.access.AgentRuntimeSnapshot;
import org.zexnocs.teanekocore.framework.description.Description;
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
 * 根据注解选择当前记忆工具，执行仍经过可信访问服务，不提供管理能力。
 * <br>2026/11/02：业务方法和参数声明替代手写 schema，保留原文意图及访问边界。
 * 原始意图在模型调用前由独立原文凭据固定，正文、身份和操作标识均不成为模型业务参数。
 * <br>2026/11/03：说明统一长期保存后的查询与维护入口。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：每类仅声明一个工具，画像和新增入口复用本类访问及回执逻辑。
 *
 * <br>2026/11/05：在宿主初始化或能力装配时编译工具目录，运行选择只读缓存。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.5.0-l
 * @version 4.8.3-l
 */
@TeaNekoAgentToolClass(value = "builtin-tools", toolPackage = "agent.memory", managedByFeature = true, requires = "memory")
public final class AgentMemoryToolSource implements IAgentToolSource {
    private java.util.function.Predicate<AgentRunRequest> preSaved = ignored -> false;
    /**
     * 2026/11/03：实际轮次记录器负责前置保存时复用其原回执，替换宿主仍保留工具入口。
     * @param recorders 实际原轮次记录端口
     */
    @org.springframework.beans.factory.annotation.Autowired
    public void setRecorders(org.springframework.beans.factory.ObjectProvider<org.zexnocs.teanekoagent.component.memory.history.IAgentHistoryRecorder> recorders) {
        preSaved = request -> recorders.getIfAvailable() instanceof org.zexnocs.teanekoagent.component.memory.history.ConversationHistoryService service && service.capturesExplicit(request);
    }
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
        // 2026/11/05：单例创建时编译工具目录，高频选择只读缓存。
        TeaNekoAgentToolMethods.prepare(this);
        this.access = Objects.requireNonNull(access, "记忆访问服务不能为空。"); this.policies = Objects.requireNonNull(policies, "记忆开关端口不能为空。");
        this.defaults = Objects.requireNonNull(defaults, "记忆读取默认策略不能为空。"); this.executor = Objects.requireNonNull(executor, "记忆工具执行器不能为空。");
        this.mapper = Objects.requireNonNull(mapper, "记忆工具 JSON 转换器不能为空。").rebuild()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
        replies = new MemoryToolReplies(mapper);
    }

    /**
     * 2026/11/05：选择本类的查询入口；画像和新增由配置组合独立提供者，缺少引用时不开放查询。
     * @param definition 当前实例定义
     * @param request 包含可信原文及已解析引用的工具选择请求
     * @param runtime 本轮运行快照，不从中读取凭据或其他权限
     * @return 可见与执行相同的明确工具白名单
     * @throws MemoryAccessException 当当前开关读取不可用时
     */
    @Override public List<LLMToolBinding> select(AgentDefinition definition, AgentRunRequest request, AgentRuntimeSnapshot runtime) {
        // 2026/11/05：这里只编译本类查询，完整内置能力由 MemoryToolConfig 组合。
        return TeaNekoAgentToolMethods.select(this, definition, request, runtime, null, mapper, executor);
    }

    /**
     * 依据实际空间和配置决定读取工具可见性，执行前后由宿主复核。
     * @param context 固定宿主上下文
     * @return 当前允许读取时为真
     */
    public boolean canRead(TeaNekoAgentToolContext context) {
        var policy = policy(context);
        return policy != null && policy.enabled() && policy.readEnabled();
    }

    /**
     * 只有当前用户明确要求记住原文时才开放新增，不从模型参数推导意图。
     * @param context 固定宿主上下文
     * @return 当前允许新增且原文意图存在时为真
     */
    public boolean canAdd(TeaNekoAgentToolContext context) {
        var policy = policy(context);
        var original = context.request().userText();
        return policy != null && policy.enabled() && policy.addEnabled() && original != null
                && MemoryAddIntent.fromUserText(expected(context), original.text()).isPresent();
    }

    /**
     * 读取真实配置，配置异常按原领域错误拒绝，不降级为允许访问。
     * @param context 固定宿主上下文
     * @return 当前配置，无记忆引用时为空
     * @throws MemoryAccessException 当配置读取失败时
     */
    private @Nullable MemoryAccessPolicy policy(TeaNekoAgentToolContext context) {
        if (context.request().memoryReference() == null) { return null; }
        try { return Objects.requireNonNull(policies.read(context.request().actor().scope()), "记忆工具策略不能为空。"); }
        catch (RuntimeException ignored) { throw new MemoryAccessException(MemoryAccessException.Reason.CONFIGURATION_ERROR); }
    }

    /**
     * 保留共享空间查询、领域预算和错误信封，业务 DTO 展开为根参数。
     * @param args 查询筛选及预算
     * @param context 宿主注入的实际调用
     * @return 有界记忆结果
     */
    @TeaNekoAgentTool(value = QUERY, condition = "canRead")
    @Description("查询当前共享空间的事实与偏好，保留主体、来源、时间和覆盖限制；部分无命中不表示确定不存在。")
    public CompletionStage<String> recall(@TeaNekoAgentToolParam(flatten = true) QueryArgs args, TeaNekoAgentToolContext context) {
        return invoke(QUERY, expected(context), null, mapper.valueToTree(args), context.requireCall(), context.requireInvocation());
    }

    /**
     * 读取指定用户画像，省略主体时使用真实发起者。
     * @param args 画像主体及预算
     * @param context 宿主注入的实际调用
     * @return 可追溯画像结果
     */
    // 2026/11/05：画像注解入口独立注册，本方法保留共享访问与参数转换。
    @Description("读取指定用户的可追溯事实/偏好画像，结果可能因预算不完整；不推断人格或关系。")
    public CompletionStage<String> profile(@TeaNekoAgentToolParam(flatten = true) ProfileArgs args, TeaNekoAgentToolContext context) {
        return invoke(PROFILE, expected(context), null, mapper.valueToTree(args), context.requireCall(), context.requireInvocation());
    }

    /**
     * 新增原始明确意图，调用标识继续使用宿主原调用 ID。
     * @param kind 事实或偏好类别
     * @param context 宿主注入的实际调用
     * @return 新增回执或固定领域错误
     */
    // 2026/11/05：新增注解入口独立注册，继续保留原文凭据和幂等回执。
    // 2026/11/03：默认保存进入新长期领域，旧读取工具继续用于旧记录。
    @Description("立即保存本轮用户明确要求记住的原文，归属于本人；只选择事实或偏好，不改写原文。默认保存到新长期记忆，用 memory_search、memory_user_profile 查询，用 memory_correct、memory_forget 维护；旧模块独立装配保留原入口。")
    public CompletionStage<String> add(@Description("FACT 为明确事实，PREFERENCE 为明确偏好。") MemoryFactValue.Kind kind,
            TeaNekoAgentToolContext context) {
        var expected = expected(context);
        var original = Objects.requireNonNull(context.request().userText(), "新增记忆缺少原文。");
        var intent = MemoryAddIntent.fromUserText(expected, original.text()).orElseThrow(() -> new MemoryAccessException(MemoryAccessException.Reason.INTENT_REQUIRED));
        // 2026/11/03：默认前置路径已经保存，工具只复用原请求回执，不依模型分类重复新增。
        if (preSaved.test(context.request())) {
            var value = new MemoryFactValue(MemoryFactValue.Kind.FACT, intent.text(), List.of(), 1,
                    MemoryFactValue.Importance.NORMAL, MemoryEventTime.unknown(), null);
            return access.append(expected, "explicit-user-request", intent, value, context.requireInvocation().control())
                    .thenApply(replies::success).exceptionally(failure -> replies.error("EXPLICIT_CAPTURE_UNAVAILABLE"));
        }
        return invoke(ADD, expected, intent, mapper.valueToTree(Map.of("kind", kind)), context.requireCall(), context.requireInvocation());
    }

    /**
     * 从不可替换的请求取得访问身份，不接受模型指定空间。
     * @param context 固定宿主上下文
     * @return 原访问身份
     */
    private static MemoryAccessContext expected(TeaNekoAgentToolContext context) {
        var request = context.request();
        return new MemoryAccessContext(request.requestId(), request.actor(), Objects.requireNonNull(request.memoryReference(), "记忆工具缺少空间引用。"));
    }

    /**
     * 查询业务参数，不包含宿主身份；省略字段沿用领域默认值。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param subjectId 可选事实主体
     * @param keywords 可选字面关键词
     * @param tags 可选精确标签
     * @param kinds 可选事实类别
     * @param start 可选事件开始时间
     * @param end 可选事件结束时间
     * @param includeEstimated 是否允许估算事件时间
     * @param limit 可选结果条数
     * @param maxCharacters 可选证据预算
     * @param maxScanned 可选扫描预算
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.6.0-l
     * @version 4.8.0-l
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record QueryArgs(
            @Nullable @Description("可选事实主体 TeaNeko UUID；省略时查询当前共享空间。") String subjectId,
            @Nullable @Description("字面关键词，最多八项，每项最多 128 字符。") List<String> keywords,
            @Nullable @Description("必须全部命中的标签，最多十六项。") List<String> tags,
            @Nullable @Description("事实或偏好类别，省略时不限。") List<MemoryFactValue.Kind> kinds,
            @Nullable @Description("可选事件开始时间，带时区的 ISO-8601 时间点。") String start,
            @Nullable @Description("可选事件结束时间，带时区的 ISO-8601 时间点。") String end,
            @Nullable @Description("未知事件是否使用记录时间估算，默认允许。") Boolean includeEstimated,
            @Nullable @Description("可选结果条数，正整数且受本地硬上限约束。") Integer limit,
            @Nullable @Description("证据字符预算，至少为 2，不能扩大本地及工具上限。") Integer maxCharacters,
            @Nullable @Description("候选检查预算，正整数且不能扩大本地上限。") Integer maxScanned) { }

    /**
     * 画像业务参数，省略主体时读取真实用户，不包含空间或授权字段。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param subjectId 可选画像主体
     * @param limit 可选结果条数
     * @param maxCharacters 可选证据预算
     * @param maxScanned 可选扫描预算
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.6.0-l
     * @version 4.8.0-l
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ProfileArgs(
            @Nullable @Description("可选 TeaNeko UUID，省略时使用真实发起者。") String subjectId,
            @Nullable @Description("可选结果条数，受本地硬上限约束。") Integer limit,
            @Nullable @Description("证据字符预算，不能扩大本地上限。") Integer maxCharacters,
            @Nullable @Description("候选检查预算，不能扩大本地上限。") Integer maxScanned) { }

    /**
     * 验证可信交接后执行业务；原调用 ID 直接来自经过工具集校验的 call，不从 JSON 重建。
     * @param name 固定工具名称
     * @param expected 选择时固定的可信身份
     * @param intent 选择时固定的明确新增意图
     * @param args 2026/11/02：由注解 DTO 转换的业务参数
     * @param call 工具集已接受的原调用
     * @param trusted 当前阶段上下文与借用控制
     * @return 实际业务完成后的完整有界信封
     */
    private CompletionStage<String> invoke(String name, MemoryAccessContext expected, @Nullable MemoryAddIntent intent,
            JsonNode args, ILLMToolCall call, ToolInvocationContext trusted) {
        try {
            trusted.control().checkActive();
            if (!MemoryToolContexts.from(trusted).equals(expected)) { throw new MemoryAccessException(MemoryAccessException.Reason.UNAVAILABLE); }
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

}
