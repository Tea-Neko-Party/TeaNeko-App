package org.zexnocs.teanekoagent.component.contact;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.component.expression.AgentReplyPlan;
import org.zexnocs.teanekoagent.component.memory.history.AgentDeliveryService;
import org.zexnocs.teanekoagent.component.memory.history.HistoryContext;
import org.zexnocs.teanekoagent.component.memory.history.HistoryMessage;
import org.zexnocs.teanekoagent.component.memory.history.IConversationHistory;
import org.zexnocs.teanekoagent.component.memory.namespace.MemoryNamespaceBindings;
import org.zexnocs.teanekoagent.component.memory.namespace.MemoryNamespaceReference;
import org.zexnocs.teanekoagent.component.memory.runtime.IAgentMemoryReferences;
import org.zexnocs.teanekoagent.component.personality.IAgentPersonalitySource;
import org.zexnocs.teanekoagent.component.social.SocialAccess;
import org.zexnocs.teanekoagent.component.tool.TeaNekoAgentToolContext;
import org.zexnocs.teanekoagent.runtime.AgentActorContext;
import org.zexnocs.teanekocore.logger.ILogger;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 私聊邀请、空间绑定、当前开关及发送防重的真实执行边界。
 * <br>2026/11/06：每个原请求只有一个发送身份，回执不明时不自动重发。
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.6-l
 * @version 4.8.6-l
 */
@Service
public final class PrivateContactService {
    private final IPrivateContactTransport transport;
    private final MemoryNamespaceBindings bindings;
    private final IAgentMemoryReferences references;
    private final SocialAccess access;
    private final AgentDeliveryService delivery;
    private final IConversationHistory history;
    private final IAgentPersonalitySource personalities;
    private final ILogger logger;

    /**
     * @param transport 客户端端口
     * @param bindings 只读空间绑定
     * @param references 原引用复核
     * @param access 当前配置
     * @param delivery 持久发送回执
     * @param history 已确认正文记录
     * @param personalities 目标人格
     * @param logger 失败报告
     */
    public PrivateContactService(IPrivateContactTransport transport, MemoryNamespaceBindings bindings,
            IAgentMemoryReferences references, SocialAccess access, AgentDeliveryService delivery,
            IConversationHistory history, IAgentPersonalitySource personalities, ILogger logger) {
        this.transport = transport;
        this.bindings = bindings;
        this.references = references;
        this.access = access;
        this.delivery = delivery;
        this.history = history;
        this.personalities = personalities;
        this.logger = logger;
    }

    /**
     * @param text 正文
     * @param invitation 邀请原文
     * @param context 真实上下文
     * @return 拒绝说明或已确认结果
     */
    public CompletionStage<String> send(String text, String invitation, TeaNekoAgentToolContext context) {
        var request = context.request();
        var control = context.requireInvocation().getControl();
        context.requireCall();
        var source = request.memoryReference();
        if (request.actor().origin() != AgentActorContext.Origin.USER || source == null
                || access.settings(request.actor().scope(), "private-contact").isEmpty()) {
            return CompletableFuture.completedStage("无法私聊：当前聊天没有启用受控私聊能力。");
        }
        if (text.isBlank() || text.length() > 2000 || request.userText() == null
                || !invited(request.userText().text(), invitation)) {
            return CompletableFuture.completedStage("无法私聊：需要本轮用户明确邀请私聊本人的原文，正文最多 2000 字。");
        }
        var target = transport.resolve(request.actor().scope(), request.actor().userId().orElseThrow());
        if (target.isEmpty()) { return CompletableFuture.completedStage("无法私聊：当前客户端没有该用户可验证的私聊映射。"); }
        if (target.get().scope().equals(request.actor().scope())) {
            return CompletableFuture.completedStage("当前已经是该用户的私聊，请直接在当前聊天回复。");
        }
        if (!access.readable(target.get().scope())) {
            return CompletableFuture.completedStage("无法私聊：该用户的私聊尚未登记、未启用 Agent 或记忆读取已关闭。");
        }
        return control.guard(bindings.readReference(target.get().scope())).thenCompose(found -> {
            if (found.isEmpty() || !sameSpace(source, found.get())) {
                return CompletableFuture.completedStage("无法私聊：该用户的私聊没有绑定同一记忆空间。请先由用户或管理员配置。");
            }
            var reference = found.get();
            var recipient = target.get();
            var id = UUID.nameUUIDFromBytes(("private-contact:" + request.requestId()).getBytes(StandardCharsets.UTF_8));
            var actor = new AgentActorContext(recipient.scope(), AgentActorContext.Origin.USER, request.actor().userId(),
                    recipient.platformId(), request.actor().sourceId(), recipient.platformId());
            Runnable guard = () -> {
                control.checkActive();
                if (access.settings(request.actor().scope(), "private-contact").isEmpty()
                        || !access.readable(request.actor().scope()) || !access.readable(recipient.scope())) {
                    throw new IllegalStateException("私聊发送前 Agent 或记忆权限已变化。");
                }
            };
            return control.guard(personalities.resolve(recipient.scope(), control)).thenCompose(personality ->
                    delivery.send(reference, actor, id, 0, 1, new AgentReplyPlan.Text(text, AgentReplyPlan.Mode.PLAIN),
                            () -> control.guard(references.verify(source)).thenCompose(ignored -> references.verify(reference))
                                    .thenCompose(ignored -> personalities.verify(recipient.scope(), personality, control))
                                    .thenCompose(ignored -> transport.send(recipient, text, guard)))
                    .thenCompose(ignored -> history.append(new HistoryContext(reference, personality),
                            new HistoryMessage(id.toString(), actor, HistoryMessage.Role.ASSISTANT, text, Instant.now(), true, false), control)
                            .exceptionally(failure -> {
                                logger.error("Agent 受控私聊", "私聊已发送但历史记录失败，不重发。请求=" + id, failure);
                                return false;
                            })).thenApply(ignored -> "已向该用户的私聊发送并收到平台回执。"));
        });
    }

    /**
     * @param source 原引用
     * @param target 目标引用
     * @return 同空间及代次
     */
    static boolean sameSpace(MemoryNamespaceReference source, MemoryNamespaceReference target) {
        return source.namespaceId().equals(target.namespaceId()) && source.generation() == target.generation();
    }

    /**
     * @param current 本轮原文
     * @param quote 连续邀请
     * @return 有明确私聊邀请且没有撤回措辞
     */
    static boolean invited(String current, String quote) {
        if (quote.length() < 2 || quote.length() > 300 || !current.contains(quote)) { return false; }
        var lower = quote.toLowerCase(Locale.ROOT);
        return (lower.contains("私聊") || lower.contains("私信") || lower.contains("dm me") || lower.contains("message me privately"))
                && !current.matches("(?is).*(别|不要|不许|禁止|无需|不用|别再|don't|do not).{0,12}(私聊|私信|dm|message me privately).*" );
    }
}
