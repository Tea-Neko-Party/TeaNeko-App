package org.zexnocs.teanekoagent.expression;

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
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.0.0
 * @version 4.6.1-l
 */
public final class ReplyExtensionRegistry {
    private final AnnotatedBeanRegistry<ReplyPlans.ProtocolKey,IReplyProtocolParser> protocols;
    private final AnnotatedBeanRegistry<ReplyPlans.DirectiveKey,IReplyDirectiveParser> directives;
    private final Map<Class<?>,PartBinding> handlers;
    private final @Nullable PluginRegistry plugins;
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
        protocols.entries().values().forEach(e -> available(e.type())); directives.entries().values().forEach(e -> available(e.type())); handlers.values().forEach(e -> available(e.ownerType()));
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
     * @param context 原上下文
     * @param draft 草稿
     * @param delay 已固定等待
     * @param tokens 当前版本集合
     * @param executor 业务域
     * @return 已解析步骤
     */
    public CompletionStage<ReplyPlans.Step> prepareAsync(ReplyCompileContext context, ReplyPlans.Draft draft, int delay, List<PluginRegistry.Token> tokens, Executor executor) {
        context.control().checkActive(); var binding = handler(draft.content()); capture(binding.ownerType(), tokens);
        return context.control().guard(resolveTyped(binding.handler(), context, draft.content())).thenApplyAsync(resolved -> {
            context.control().checkActive();
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
    private static <P extends ReplyPlans.Content> CompletionStage<P> resolveTyped(IReplyPartHandler<P> handler, ReplyCompileContext context, ReplyPlans.Content content) {
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
    private static <P extends ReplyPlans.Content> ReplyPlans.Step prepareTyped(IReplyPartHandler<P> handler, ReplyPlans.Draft draft, int delay) {
        return handler.prepare(handler.type().cast(draft.content()), draft.mode(), delay);
    }
    /**
     * 只返回原注册处理器。
     * @param content 内容
     * @return 准确处理器
     * @throws IllegalArgumentException 当类型未注册时
     */
    private PartBinding handler(ReplyPlans.Content content) {
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
    private static <P extends ReplyPlans.Content> ILLMContent historyTyped(IReplyPartHandler<P> handler, ReplyPlans.Content content) { return handler.history(handler.type().cast(content)); }
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
     * @param context 原发送上下文
     * @param step 冻结步骤
     * @return 真实传输阶段
     */
    public CompletionStage<Void> send(ReplySendContext context,ReplyPlans.Step step){
        // 2026/11/03：公共入口始终先守卫，不依赖扩展是否记得调用上下文守卫。
        var binding=handler(step.content());
        return context.guard().get().thenCompose(ignored->{
            context.control().checkActive();if(!available(binding.ownerType())){throw new IllegalStateException("回复扩展已停用。");}
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
    private static <P extends ReplyPlans.Content> CompletionStage<Void> sendContextTyped(IReplyPartHandler<P> handler,ReplySendContext context,ReplyPlans.Step step){
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
    private static <P extends ReplyPlans.Content> CompletionStage<Void> sendTyped(IReplyPartHandler<P> handler, ITeaNekoMessageData data, ReplyPlans.Step step) { return handler.send(data, handler.type().cast(step.content()), step.mode()); }
    /**
     * 重放不重新解析，只检查原扩展版本和类型。
     * @param plan 原计划
     */
    public void verify(ReplyPlans.Approved plan) {
        if (plugins != null) { plan.tokens().forEach(plugins::check); }
        plan.steps().forEach(step -> { var value = handler(step.content()); if (!value.id().equals(step.type()) || value.version()!=step.version() || !available(value.ownerType())) { throw new IllegalStateException("回复处理器版本不可用。"); } });
    }
    /**
     * 未声明归属的宿主实现沿用 Agent 插件，独立宿主没有插件目录时仍可使用。
     * @param type 目标类型
     * @return 真实归属
     */
    private static String owner(Class<?> type) { var owned = AnnotatedElementUtils.findMergedAnnotation(type, PluginOwned.class); return owned == null ? "teaneko.agent" : owned.value(); }
    /**
     * 检查注册身份和实际启用。
     * @param type 目标类型
     * @return 可用时为真
     * @throws IllegalArgumentException 当声明插件归属但缺失插件目录时
     */
    private boolean available(Class<?> type) {
        if (plugins == null && AnnotatedElementUtils.findMergedAnnotation(type,PluginOwned.class)!=null) { throw new IllegalArgumentException("声明插件归属的回复扩展缺少插件目录。"); }
        return plugins == null || plugins.isEnabled(owner(type));
    }
    /**
     * 固定原所属插件版本。
     * @param type 目标类型
     * @param tokens 本次集合
     */
    private void capture(Class<?> type, List<PluginRegistry.Token> tokens) {
        if (plugins != null) { var token = plugins.capture(owner(type)); if (!tokens.contains(token)) { tokens.add(token); } }
    }
    /**
     * 固定内容声明与泛型处理器代理。
     * @param id 内容 ID
     * @param version 内容版本
     * @param ownerType 注解目标类
     * @param handler 原容器代理
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.0.0
     * @version 4.6.1-l
     */
    private record PartBinding(String id, int version, Class<?> ownerType, IReplyPartHandler<?> handler) {}
}
