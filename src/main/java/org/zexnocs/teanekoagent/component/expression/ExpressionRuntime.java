package org.zexnocs.teanekoagent.component.expression;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.component.llm.framework.interaction.LLMInteractionResult;
import org.zexnocs.teanekoagent.component.llm.framework.message.LLMAssistantMessage;
import org.zexnocs.teanekoagent.component.llm.framework.message.LLMMessageCodec;
import org.zexnocs.teanekoagent.component.llm.framework.message.LLMProtocolSnapshots;
import org.zexnocs.teanekoagent.component.llm.framework.message.interfaces.ILLMAssistantMessage;
import org.zexnocs.teanekoagent.component.llm.framework.message.interfaces.ILLMContent;
import org.zexnocs.teanekoagent.runtime.AgentActivity;
import org.zexnocs.teanekoagent.runtime.AgentRunRequest;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekoagent.runtime.prompt.AgentPromptInput;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import org.zexnocs.teanekocore.utils.StableDigest;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * 表达合同、计划编译和语义投影；不执行模型或平台发送。
 * <br>2026/11/03：资源解析使用当前真实工具回执，按实际步骤数冻结等待。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：协议头识别与控制行解析统一允许边缘空白。
 *
 * <br>2026/11/05：格式失败使用安全原因类型，允许日志与用户回执准确解释。
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.8.2-l
 */
public final class ExpressionRuntime {
    public static final String FRAGMENT = "expression_contract";
    private static final Pattern HEADER = Pattern.compile("^⟦TNK:([a-z0-9]{12})\\|BEGIN\\|protocol=([a-z][a-z0-9_.-]{0,47}:[a-z][a-z0-9_.-]{0,47})\\|v=([1-9][0-9]{0,3})⟧$");
    private final ReplyExtensionRegistry registry;
    private final Supplier<ExpressionSettings> settings;
    private final Predicate<AgentScope> enabled;
    private final LLMMessageCodec codec;
    private final Executor executor;
    /**
     * 保存无 I/O 的端口，配置只在实际轮次读取。
     * @param registry 注册目录
     * @param settings 动态本地设置
     * @param enabled 当前 scope 开关
     * @param mapper 原协议转换器
     */
    public ExpressionRuntime(ReplyExtensionRegistry registry, Supplier<ExpressionSettings> settings, Predicate<AgentScope> enabled, ObjectMapper mapper) {
        this(registry, settings, enabled, mapper, Runnable::run);
    }
    /**
     * 默认宿主借用业务执行器，数据库完成线程只交接后续动作。
     * @param registry 注册目录
     * @param settings 本地设置
     * @param enabled 当前开关
     * @param mapper 转换器
     * @param executor 业务域
     */
    public ExpressionRuntime(ReplyExtensionRegistry registry, Supplier<ExpressionSettings> settings, Predicate<AgentScope> enabled, ObjectMapper mapper, Executor executor) {
        this.registry = registry; this.settings = settings; this.enabled = enabled; this.executor = executor; codec = new LLMMessageCodec(mapper);
    }
    /** @return 当前本地有界设置 */
    public ExpressionSettings settings() { return settings.get(); }
    /** @return 原内容分派器 */
    public ReplyExtensionRegistry registry() { return registry; }
    /**
     * 当轮标识随重试稳定，不作为授权。
     * @param request 请求 ID
     * @return 十二位标识
     */
    public static String nonce(UUID request) { return StableDigest.sha256("reply-contract-v1", request.toString()).substring(0,12); }
    /**
     * 仅原生对话活动且开关有效时提供表达合同。
     * @param request 固定请求
     * @return 可用时为真
     */
    public boolean enabled(AgentRunRequest request) {
        return settings().enabled() && enabled.test(request.actor().scope()) && (request.activity() == AgentActivity.COMMAND || request.activity() == AgentActivity.AUTOMATIC_REPLY);
    }
    /**
     * 提供固定原人格基础上的表达约束，不新增风格模型调用。
     * @param request 固定请求
     * @return 带当轮标识及目录版本的完整说明
     */
    public String contract(AgentRunRequest request) {
        return "宿主表达合同：沿用当前人格和对象偏好。闲聊可少用句末标点、自然口癖及少量颜文字，不要每句强行使用。技术、代码、链接、数值、正式说明保留必要格式。"
                + "需要多次发送时用完整信封一次输出所有分段，不再逐段调用模型；只使用下列已提供指令，不猜测表情包或原生表情。普通图文和旧JSON仍可用。\n"
                + "当前限制=" + settings() + "\n" + registry.instructions(nonce(request.requestId()));
    }
    /**
     * 资源解析允许异步，所有步骤完整冻结之后才产生可发送计划。
     * @param request 原请求
     * @param input 实际布局
     * @param interaction 原交互
     * @param control 原借用轮次控制
     * @return 完整计划或没有表达能力
     * @throws IllegalArgumentException 当协议、数量或等待语法无效时
     * @throws IllegalStateException 当原表达能力已经关闭时
     */
    public CompletionStage<ReplyPlans.@Nullable Approved> compile(AgentRunRequest request, AgentPromptInput input, LLMInteractionResult interaction, ExecutionControl control) {
        control.checkActive();
        if (interaction.status() != LLMInteractionResult.Status.SUCCESS) { return CompletableFuture.completedStage(null); }
        var answer = interaction.finalCandidate().orElseThrow();
        String text = answer.getContents().stream().filter(c -> c.getContentPart() instanceof org.zexnocs.teanekoagent.component.llm.framework.message.content.TextLLMContentPart)
                .map(c -> ((org.zexnocs.teanekoagent.component.llm.framework.message.content.TextLLMContentPart)c.getContentPart()).getText()).collect(java.util.stream.Collectors.joining("\n")).strip();
        boolean adopted = input.fragments().stream().anyMatch(f -> f.name().equals(FRAGMENT));
        // 2026/11/05：格式错误只携带可信固定原因，不记录模型原文。
        if (!adopted) {
            if (text.startsWith("⟦TNK:")) {
                throw new ReplyProtocolException("本轮没有可用的表达协议合同。");
            }
            return CompletableFuture.completedStage(null);
        }
        if (!enabled(request)) { throw new IllegalStateException("本轮拟人表达已经关闭。"); }
        var key = new ReplyPlans.ProtocolKey(text.startsWith("{") && text.contains("\"teaneko_reply\"") ? "teaneko:json-reply" : "teaneko:plain",1);
        boolean directed = text.startsWith("⟦TNK:");
        if (directed) {
            // 2026/11/05：只规范化协议头的边缘空白，不改变原始正文或插件收到的答案。
            var first = text.lines().findFirst().orElse("").strip();
            var match = HEADER.matcher(first);
            if (!match.matches() || !match.group(1).equals(nonce(request.requestId()))) {
                throw new ReplyProtocolException("回复协议头或当轮标识无效。");
            }
            key = new ReplyPlans.ProtocolKey(match.group(2), Integer.parseInt(match.group(3)));
        }
        var tokens = new ArrayList<PluginRegistry.Token>(); var drafts = registry.parse(key, answer, nonce(request.requestId()), tokens);
        var limits = settings();
        if (drafts.size() > (directed ? limits.maxMessages() : 32)) {
            throw new ReplyProtocolException("回复消息数量超出当前策略。");
        }
        var steps = new ArrayList<ReplyPlans.Step>(); int total = 0;
        // 2026/11/03：仅本次交互回执可授权资源；异步链尚未执行时不能用 steps.size 判断首段。
        var context = new ReplyCompileContext(request, input, control,interaction.tools(),drafts);
        CompletionStage<Void> compiled = CompletableFuture.completedStage(null);
        for (int draftIndex=0;draftIndex<drafts.size();draftIndex++) {
            var draft=drafts.get(draftIndex);boolean first=draftIndex==0;
            int delay = draft.waitMillis() == null ? (first ? 0 : limits.defaultDelay() + 40 * Math.min(36, draft.content() instanceof ReplyPlans.Text(
                    String value
            ) ? value.codePointCount(0, value.length()) : 8)) : draft.waitMillis();
            if (delay < 0 || delay > 60000) {
                throw new ReplyProtocolException("回复等待无效。");
            }
            delay = Math.min(delay, Math.min(first ? Math.min(500,limits.maxStepDelay()) : limits.maxStepDelay(), limits.maxTotalDelay()-total));
            total += delay; final int fixedDelay = delay;
            compiled = compiled.thenComposeAsync(ignored -> registry.prepareAsync(context, draft, fixedDelay, tokens, executor)
                    .thenAccept(steps::add), executor);
        }
        var protocol = key;
        return compiled.thenApplyAsync(ignored -> {
            control.checkActive();
            return ReplyPlans.approved(request.requestId(), protocol, digest(answer), steps, tokens, semanticAnswer(steps));
        }, executor);
    }
    /**
     * 原答案摘要用于防止历史工厂改配另一份内容。
     * @param answer 正式答案
     * @return 稳定摘要
     */
    public String digest(ILLMAssistantMessage answer) { return StableDigest.sha256(codec.signature(List.of(answer)).toString()); }
    /**
     * 从已验证计划生成普通正文和图片，不沉积控制指令。
     * @param plan 固定计划
     * @return 不可变正式消息
     */
    public ILLMAssistantMessage answer(ReplyPlans.Approved plan) {
        return plan.answer();
    }
    /**
     * 编译时生成一次不可变历史正文，重放不重新调用处理器。
     * 2026/11/05：容量失败按固定安全原因上报，不包含语义正文或媒体内容。
     * @param steps 完整步骤
     * @return 冻结语义消息
     * @throws IllegalArgumentException 当整体正文、图片或编码容量超限时
     */
    private ILLMAssistantMessage semanticAnswer(List<ReplyPlans.Step> steps) {
        if (steps.stream().mapToLong(step -> step.text().length()).sum() > 64000) {
            throw new ReplyProtocolException("回复语义文本超出总容量。");
        }
        var contents = steps.stream().map(registry::history).toList();
        var images = contents.stream().map(ILLMContent::getContentPart)
                .filter(org.zexnocs.teanekoagent.component.llm.framework.message.content.ImageLLMContentPart.class::isInstance)
                .map(org.zexnocs.teanekoagent.component.llm.framework.message.content.ImageLLMContentPart.class::cast).toList();
        if (images.size() > 4 || images.stream().mapToLong(image -> image.getUrl().length()).sum() > 2097152) {
            throw new ReplyProtocolException("回复媒体超出总容量。");
        }
        var answer = (ILLMAssistantMessage) LLMProtocolSnapshots.messages(List.of(LLMAssistantMessage.builder().contents(contents).build())).getFirst();
        if (codec.signature(List.of(answer)).bytes() > 3000000) {
            throw new ReplyProtocolException("回复总投影超出字节预算。");
        }
        return answer;
    }
    /**
     * 实际使用前复核原插件及内容类型，不重新解析或选图。
     * @param plan 固定计划
     */
    public void verify(ReplyPlans.Approved plan) { registry.verify(plan); }
}
