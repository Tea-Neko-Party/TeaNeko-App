package org.zexnocs.teanekoplugin.agent.expression.expression;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

// 2026/11/07：表达业务归入插件，通用交付能力独立于业务组件。

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.jspecify.annotations.Nullable;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMAssistantMessage;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMContent;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.framework.extension.AnnotatedBeanRegistry;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import org.zexnocs.teanekocore.utils.StableDigest;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;

import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

/**
 * 按协议、指令和准确内容类型分派，保留容器代理与所属插件版本。
 * <br>2026/11/03：资源发送交接原请求和控制，不丢失异步后的守卫。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * <br>2026/11/05：通过明确的 getter 借用执行资源，保持异步完成后的原关闭责任。
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.9.0-l
 */
public final class ReplyExtensionRegistry {
    private final AnnotatedBeanRegistry<ReplyPlans.ProtocolKey,IReplyProtocolParser> protocols;
    private final AnnotatedBeanRegistry<ReplyPlans.DirectiveKey,IReplyDirectiveParser> directives;
    private final Map<Class<?>,PartBinding> handlers;
    private final @Nullable PluginRegistry plugins;
    // 2026/11/07：归属只在初始化解析，模型与发送路径复用缓存。
    private final Map<Class<?>, Optional<String>> owners;
    /**
     * 在宿主装配期扫描，构造不访问模型或平台。
     * @param scanner 原 Bean 扫描器
     * @param plugins 可选独立宿主插件目录
     * @throws IllegalArgumentException 当声明、类型或键冲突时
     */
    public ReplyExtensionRegistry(IBeanScanner scanner, @Nullable PluginRegistry plugins) {
        this.plugins = plugins;
        protocols = new AnnotatedBeanRegistry<>(scanner, TeaNekoReplyProtocol.class, IReplyProtocolParser.class,
                a -> new ReplyPlans.ProtocolKey(a.value(), a.version()));
        directives = new AnnotatedBeanRegistry<>(scanner, TeaNekoReplyDirective.class, IReplyDirectiveParser.class, a -> {
            if (Set.of("BEGIN","END").contains(a.value()) || !a.value().matches("[A-Z][A-Z0-9_]{0,47}")) { throw new IllegalArgumentException("回复指令声明名称无效。"); }
            return new ReplyPlans.DirectiveKey(new ReplyPlans.ProtocolKey(a.protocol(), a.version()), a.value());
        });
        var parts = new AnnotatedBeanRegistry<>(scanner, TeaNekoReplyPartHandler.class, IReplyPartHandler.class, a -> new ReplyPlans.ProtocolKey(a.value(),a.version()));
        var found = new LinkedHashMap<Class<?>,PartBinding>();
        parts.entries().forEach((key, entry) -> {
            IReplyPartHandler<?> handler = entry.bean();
            if (found.putIfAbsent(handler.type(), new PartBinding(key.id(), key.version(), entry.type(), handler)) != null) { throw new IllegalArgumentException("回复内容类型存在多个处理器：" + handler.type().getName()); }
        });
        handlers = Map.copyOf(found);
        if (protocols.entries().size() > 32 || directives.entries().size() > 64 || handlers.size() > 64) { throw new IllegalArgumentException("回复扩展目录超出容量。"); }
        var registeredOwners = new LinkedHashMap<Class<?>, Optional<String>>();
        var types = new LinkedHashSet<Class<?>>();
        protocols.entries().values().forEach(entry -> types.add(entry.type()));
        directives.entries().values().forEach(entry -> types.add(entry.type()));
        handlers.values().forEach(entry -> types.add(entry.ownerType()));
        for (var type : types) {
            var owned = AnnotatedElementUtils.findMergedAnnotation(type, PluginOwned.class);
            registeredOwners.put(type, Optional.ofNullable(owned == null ? null : owned.value()));
        }
        owners = Map.copyOf(registeredOwners);
        types.forEach(this::available);
    }
    /**
     * 生成当前可用语法及版本摘要，关闭再启用也改变摘要。
     * @param nonce 原当轮标识
     * @return 有界目录说明
     * @throws IllegalArgumentException 当目录说明超出容量时
     */
    public String instructions(String nonce) {
        var parts = new ArrayList<String>(); var tokens = new ArrayList<PluginRegistry.Token>();
        protocols.entries().entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().toString())).forEach(e -> {
            if (available(e.getValue().type())) { capture(e.getValue().type(), tokens); parts.add(e.getValue().bean().instructions(nonce)); }
        });
        directives.entries().entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().toString())).forEach(e -> {
            if (available(e.getValue().type()) && protocols.entries().containsKey(e.getKey().protocol()) && available(protocols.require(e.getKey().protocol()).type())) {
                capture(e.getValue().type(), tokens); parts.add(e.getKey() + "：" + e.getValue().bean().instructions());
            }
        });
        handlers.values().forEach(e -> { if (available(e.ownerType())) { capture(e.ownerType(), tokens); } });
        String text = String.join("\n", parts);
        if (text.length() > 8000) { throw new IllegalArgumentException("回复扩展说明超出预算。"); }
        return "目录版本=" + StableDigest.sha256(tokens.toString(), text) + "\n" + text;
    }
    /**
     * 只允许精确协议与同协议指令，收集本次实际扩展版本。
     * @param key 协议
     * @param answer 正式答案
     * @param nonce 当轮标识
     * @param tokens 当前版本接收集合
     * @return 有类型草稿
     */
    public List<ReplyPlans.Draft> parse(ReplyPlans.ProtocolKey key, ILLMAssistantMessage answer, String nonce, List<PluginRegistry.Token> tokens) {
        var protocol = protocols.require(key); capture(protocol.type(), tokens);
        return List.copyOf(protocol.bean().parse(answer, nonce, command -> {
            var parser = directives.require(new ReplyPlans.DirectiveKey(key, command.name())); capture(parser.type(), tokens); return parser.bean().parse(command);
        }));
    }
    /**
     * 在实际原上下文解析资源，完成后切回业务域校验；取消不交付迟到计划。
     * 2026/11/05：通过明确的 getter 借用执行资源，保持异步完成后的原关闭责任。
     * @param context 原上下文
     * @param draft 草稿
     * @param delay 已固定等待
     * @param tokens 当前版本集合
     * @param executor 业务域
     * @return 已解析步骤
     */
    public CompletionStage<ReplyPlans.Step> prepareAsync(ReplyCompileContext context, ReplyPlans.Draft draft, int delay, List<PluginRegistry.Token> tokens, Executor executor) {
        context.getControl().checkActive(); var binding = handler(draft.content()); capture(binding.ownerType(), tokens);
        return context.getControl().guard(resolveTyped(binding.handler(), context, draft.content())).thenApplyAsync(resolved -> {
            context.getControl().checkActive();
            if (plugins != null) { tokens.forEach(plugins::check); }
            return prepare(new ReplyPlans.Draft(resolved, draft.mode(), draft.waitMillis()), delay, tokens);
        }, executor);
    }
    /**
     * 以原代理进行受检查的泛型资源解析。
     * @param handler 原处理器
     * @param context 可信上下文
     * @param content 有类型内容
     * @param <P> 内容类型
     * @return 该类型资源解析
     */
    private static <P extends ReplyPlans.IContent> CompletionStage<P> resolveTyped(IReplyPartHandler<P> handler, ReplyCompileContext context, ReplyPlans.IContent content) {
        return handler.resolve(context, handler.type().cast(content));
    }
    /**
     * 验证内容类型与声明，避免处理器伪造其他类型或等待。
     * @param draft 原草稿
     * @param delay 宿主等待
     * @param tokens 实际扩展版本
     * @return 冻结步骤
     * @throws IllegalArgumentException 当内容、声明或返回合同不一致时
     */
    private ReplyPlans.Step prepare(ReplyPlans.Draft draft, int delay, List<PluginRegistry.Token> tokens) {
        var binding = handler(draft.content()); capture(binding.ownerType(), tokens);
        var value = prepareTyped(binding.handler(), draft, delay);
        if (!value.type().equals(binding.id()) || value.version() != binding.version() || value.mode() != draft.mode() || value.waitMillis() != delay || value.text().length() > 64000
                || !value.digest().matches("[0-9a-f]{64}") || value.content().getClass() != draft.content().getClass()) { throw new IllegalArgumentException("回复内容处理器返回合同无效。"); }
        return value;
    }
    /**
     * 经准确类型转换调用泛型扩展，避免未检查强转。
     * @param handler 原代理
     * @param draft 草稿
     * @param delay 等待
     * @param <P> 内容类型
     * @return 本类型步骤
     */
    private static <P extends ReplyPlans.IContent> ReplyPlans.Step prepareTyped(IReplyPartHandler<P> handler, ReplyPlans.Draft draft, int delay) {
        return handler.prepare(handler.type().cast(draft.content()), draft.mode(), delay);
    }
    /**
     * 只返回原注册处理器。
     * @param content 内容
     * @return 准确处理器
     * @throws IllegalArgumentException 当类型未注册时
     */
    private PartBinding handler(ReplyPlans.IContent content) {
        var binding = handlers.get(content.getClass()); if (binding == null) { throw new IllegalArgumentException("未注册的回复内容类型。"); } return binding;
    }
    /**
     * 生成已校验内容的普通历史。
     * @param step 固定步骤
     * @return 普通消息内容
     */
    public ILLMContent history(ReplyPlans.Step step) { return historyTyped(handler(step.content()).handler(), step.content()); }
    /**
     * 保留泛型类型检查。
     * @param handler 原代理
     * @param content 内容
     * @param <P> 内容类型
     * @return 普通消息内容
     */
    private static <P extends ReplyPlans.IContent> ILLMContent historyTyped(IReplyPartHandler<P> handler, ReplyPlans.IContent content) {
        return handler.history(handler.type().cast(content));
    }
    /**
     * 在实际传输前检查所属插件；授权由外层原守卫检查。
     * @param data 原目标
     * @param step 固定步骤
     * @return 真实传输阶段
     */
    public CompletionStage<Void> send(ITeaNekoMessageData data, ReplyPlans.Step step) {
        var binding = handler(step.content()); if (!available(binding.ownerType())) { throw new IllegalStateException("回复扩展已停用。"); }
        return sendTyped(binding.handler(), data, step);
    }
    /**
     * 2026/11/03：通过原容器代理发送资源内容。
     * 2026/11/05：通过明确的 getter 借用执行资源，保持异步完成后的原关闭责任。
     * @param context 原发送上下文
     * @param step 冻结步骤
     * @return 真实传输阶段
     */
    public CompletionStage<Void> send(ReplySendContext context,ReplyPlans.Step step){
        // 2026/11/03：公共入口始终先守卫，不依赖扩展是否记得调用上下文守卫。
        var binding=handler(step.content());
        return context.guard().get().thenCompose(ignored->{
            context.getControl().checkActive();if(!available(binding.ownerType())){throw new IllegalStateException("回复扩展已停用。");}
            return sendContextTyped(binding.handler(),context,step);
        });
    }
    /**
     * 2026/11/03：保留准确类型和上下文重载。
     * @param handler 容器处理器
     * @param context 原上下文
     * @param step 固定步骤
     * @param <P> 内容类型
     * @return 实际传输阶段
     */
    private static <P extends ReplyPlans.IContent> CompletionStage<Void> sendContextTyped(IReplyPartHandler<P> handler,ReplySendContext context,ReplyPlans.Step step){
        return handler.send(context,handler.type().cast(step.content()),step.mode());
    }
    /**
     * 保留泛型类型检查及原代理调用。
     * @param handler 处理器
     * @param data 目标
     * @param step 步骤
     * @param <P> 内容类型
     * @return 实际回执
     */
    private static <P extends ReplyPlans.IContent> CompletionStage<Void> sendTyped(IReplyPartHandler<P> handler, ITeaNekoMessageData data, ReplyPlans.Step step) {
        return handler.send(data, handler.type().cast(step.content()), step.mode());
    }
    /**
     * 重放不重新解析，只检查原扩展版本和类型。
     * @param plan 原计划
     */
    public void verify(ReplyPlans.Approved plan) {
        if (plugins != null) { plan.tokens().forEach(plugins::check); }
        plan.steps().forEach(step -> { var value = handler(step.content()); if (!value.id().equals(step.type()) || value.version()!=step.version() || !available(value.ownerType())) { throw new IllegalStateException("回复处理器版本不可用。"); } });
    }
    /**
     * 初始化已确认的归属，独立未声明扩展不冒充宿主插件。
     * @param type 目标类型
     * @return 真实归属
     */
    private @Nullable String owner(Class<?> type) {
        return Objects.requireNonNull(owners.get(type), "回复扩展归属尚未初始化。").orElse(null);
    }
    /**
     * 检查注册身份和实际启用。
     * @param type 目标类型
     * @return 可用时为真
     * @throws IllegalArgumentException 当声明插件归属但缺失插件目录时
     */
    private boolean available(Class<?> type) {
        var owner = owner(type);
        if (plugins == null && owner != null) {
            throw new IllegalArgumentException("声明插件归属的回复扩展缺少插件目录。");
        }
        return owner == null || Objects.requireNonNull(plugins).isEnabled(owner);
    }
    /**
     * 固定原所属插件版本。
     * @param type 目标类型
     * @param tokens 本次集合
     */
    private void capture(Class<?> type, List<PluginRegistry.Token> tokens) {
        var owner = owner(type);
        if (plugins != null && owner != null) {
            var token = plugins.capture(owner);
            if (!tokens.contains(token)) {
                tokens.add(token);
            }
        }
    }
    /**
     * 固定内容声明与泛型处理器代理。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param id 内容 ID
     * @param version 内容版本
     * @param ownerType 注解目标类
     * @param handler 原容器代理
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.8.0-l
     */
    private record PartBinding(String id, int version, Class<?> ownerType, IReplyPartHandler<?> handler) {}
}
