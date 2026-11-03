package org.zexnocs.teanekoagent.history;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.agent.AgentRunRequest;
import org.zexnocs.teanekoagent.agent.AgentScope;
import org.zexnocs.teanekoagent.agent.history.IAgentHistoryRecorder;
import org.zexnocs.teanekoagent.agent.memory.AgentMemoryReferenceException;
import org.zexnocs.teanekoagent.agent.memory.IAgentMemoryReferences;
import org.zexnocs.teanekoagent.agent.personality.IAgentPersonalitySource;
import org.zexnocs.teanekoagent.agent.prompt.AgentPromptException;
import org.zexnocs.teanekoagent.agent.prompt.AgentPromptInput;
import org.zexnocs.teanekoagent.llm.framework.message.content.TextLLMContentPart;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMAssistantMessage;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMContent;
import org.zexnocs.teanekoagent.memory.access.ExplicitMemoryCapture;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.actuator.execution.ExecutionStoppedException;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 在业务域组合历史授权、时间保留与存储，并在默认轮次前等待可信明确保存。
 * <br>2026/11/03：默认轮次前先等待用户明确原文保存，关闭历史采集不关闭明确保存。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.5.0-l
 * @version 4.6.2-l
 */
public final class ConversationHistoryService implements IConversationHistory, IAgentHistoryRecorder {
    private final IHistoryStore store;
    private final IAgentMemoryReferences references;
    private final IAgentPersonalitySource personalities;
    private final Function<AgentScope, Policy> policies;
    private final Executor executor;
    private final Clock clock;
    private final Supplier<Duration> retention;
    private @Nullable ExplicitMemoryCapture explicit;
    /**
     * 2026/11/03：默认宿主注入前置保存，旧独立历史服务仍可不安装。
     * @param explicit 当前明确保存边界
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setExplicit(@Nullable ExplicitMemoryCapture explicit) { this.explicit = explicit; }
    /**
     * 2026/11/03：工具检查实际轮次记录器，复用已经前置完成的保存回执。
     * @param request 原请求
     * @return 当前记录器负责本轮明确保存时为真
     */
    public boolean capturesExplicit(AgentRunRequest request) { return explicit != null && explicit.handles(request); }
    /**
     * 借用现有资源，构造期间不读数据或启动任务。
     * @param store 历史事务端口
     * @param references 当前空间复核端口
     * @param personalities 当前人格复核端口
     * @param policies 动态作用域开关
     * @param executor 借用业务执行器
     * @param clock 时间来源
     * @param retention 懒读取保留期，一至三百六十五天；非法值仅在业务调用中拒绝
     */
    public ConversationHistoryService(IHistoryStore store, IAgentMemoryReferences references, IAgentPersonalitySource personalities,
            Function<AgentScope, Policy> policies, Executor executor, Clock clock, Supplier<Duration> retention) {
        this.store = Objects.requireNonNull(store, "历史存储不能为空。"); this.references = Objects.requireNonNull(references, "历史空间端口不能为空。");
        this.personalities = Objects.requireNonNull(personalities, "历史人格端口不能为空。"); this.policies = Objects.requireNonNull(policies, "历史开关不能为空。");
        this.executor = Objects.requireNonNull(executor, "历史执行器不能为空。"); this.clock = Objects.requireNonNull(clock, "历史时钟不能为空。");
        this.retention = Objects.requireNonNull(retention, "历史保留期来源不能为空。");
    }
    /**
     * 追加前后复核同一引用，已开始事务不因观察取消而声称回滚。
     * @param context 当前固定边界
     * @param message 真实来源消息
     * @param control 借用控制
     * @return 提交后的新增结果，采集关闭或消息已过保留期时为 false
     */
    @Override public CompletionStage<Boolean> append(HistoryContext context, HistoryMessage message, ExecutionControl control) {
        return controlled(control, () -> {
            if (!policy(context).capture()) { return CompletableFuture.completedStage(false); }
            if (!message.source().scope().equals(context.reference().scope())) { throw new IllegalArgumentException("历史来源与当前作用域不一致。"); }
            var duration = Objects.requireNonNull(retention.get(), "历史保留期不能为空。");
            if (duration.compareTo(Duration.ofDays(1)) < 0 || duration.compareTo(Duration.ofDays(365)) > 0) { throw new IllegalArgumentException("历史保留期必须在一至三百六十五天之间。"); }
            var expires = message.occurredAt().plus(duration);
            if (!expires.isAfter(clock.instant())) { return CompletableFuture.completedStage(false); }
            return verify(context, control, false).thenComposeAsync(ignored -> store.append(context, message, expires), executor)
                    .thenComposeAsync(added -> verify(context, control, false).thenApply(ignored -> added), executor);
        });
    }
    /**
     * 只查当前已授权分区，查询结果交付前再次复核配置、人格与空间。
     * @param context 当前固定边界
     * @param query 有界查询
     * @param control 借用控制
     * @return 有来源及覆盖标志的历史页面
     */
    @Override public CompletionStage<HistoryPage> search(HistoryContext context, HistoryQuery query, ExecutionControl control) {
        return controlled(control, () -> verify(context, control, true)
                .thenComposeAsync(ignored -> store.search(context, query, clock.instant()), executor)
                .thenComposeAsync(page -> verify(context, control, true).thenApply(ignored -> page), executor));
    }
    /**
     * 从实际轮次提取原始用户凭据或纯文本答案，不序列化整个交互。
     * @param request 实际固定请求
     * @param input 实际人格及输入布局
     * @param answer 可交付答案；null 表示用户输入
     * @param control 借用控制
     * @return 已提交或明确无可采集内容的完成信号
     */
    @Override public CompletionStage<Void> record(AgentRunRequest request, AgentPromptInput input, @Nullable ILLMAssistantMessage answer, ExecutionControl control) {
        // 2026/11/03：模型尚未执行时完成明确保存；输出记录不再次触发同一用户意图。
        return controlled(control, () -> (answer == null && explicit != null ? explicit.capture(request, control) : CompletableFuture.<Void>completedStage(null))
                .thenComposeAsync(ignored -> recordHistory(request, input, answer, control), executor));
    }
    /**
     * 2026/11/03：保存原历史职责独立于明确保存，继续只记录可信本轮输入或答案。
     * @param request 原请求
     * @param input 原布局
     * @param answer 可选助手答案
     * @param control 原控制
     * @return 原历史提交阶段
     */
    private CompletionStage<Void> recordHistory(AgentRunRequest request, AgentPromptInput input, @Nullable ILLMAssistantMessage answer, ExecutionControl control) {
        // 自主控制信封不是发言，交付后的真实正文由自主发送边界单独记录。
        if (request.activity() == org.zexnocs.teanekoagent.agent.AgentActivity.PROACTIVE) { return CompletableFuture.completedStage(null); }
        if (request.memoryReference() == null || input.personality() == null || answer == null && request.userText() == null) {
            return CompletableFuture.completedStage(null);
        }
        var original = request.userText();
        var text = answer == null ? Objects.requireNonNull(original).text() : answer.getContents().stream().map(ILLMContent::getContentPart)
                .filter(TextLLMContentPart.class::isInstance).map(TextLLMContentPart.class::cast).map(TextLLMContentPart::getText).collect(Collectors.joining("\n"));
        if (text.isBlank()) { return CompletableFuture.completedStage(null); }
        var role = answer == null ? HistoryMessage.Role.USER : HistoryMessage.Role.ASSISTANT;
        var id = answer == null ? Objects.requireNonNull(original).sourceId() : request.requestId().toString();
        var actualTime = answer == null ? original.occurredAt() : null;
        var message = new HistoryMessage(id, request.actor(), role, text.substring(0, Math.min(text.length(), 65536)),
                actualTime == null ? clock.instant() : actualTime, actualTime == null, text.length() > 65536);
        return append(new HistoryContext(request.memoryReference(), input.personality()), message, control).thenApply(ignored -> null);
    }
    /**
     * 读取真实登记，注销或禁用不能退回默认允许。
     * @param context 当前边界
     * @return 当前允许的独立开关
     */
    private Policy policy(HistoryContext context) {
        var policy = Objects.requireNonNull(policies.apply(context.reference().scope()), "历史策略不能为空。");
        if (!policy.enabled()) { throw new AgentPromptException(AgentPromptException.Reason.DISABLED); }
        return policy;
    }
    /**
     * 在使用前检查动态开关、固定空间与原人格，不刷新当前上下文。
     * @param context 原边界
     * @param control 借用控制
     * @param read 是否检查读取开关，否则检查采集开关
     * @return 仍可使用的完成信号
     */
    private CompletionStage<Void> verify(HistoryContext context, ExecutionControl control, boolean read) {
        control.checkActive(); var policy = policy(context);
        if (read ? !policy.read() : !policy.capture()) { throw new AgentPromptException(AgentPromptException.Reason.DISABLED); }
        return control.guard(references.verify(context.reference())).thenComposeAsync(ignored -> {
            control.checkActive(); return personalities.verify(context.reference().scope(), context.personality(), control);
        }, executor).thenRunAsync(() -> {
            control.checkActive(); var current = policy(context);
            if (read ? !current.read() : !current.capture()) { throw new AgentPromptException(AgentPromptException.Reason.DISABLED); }
        }, executor);
    }
    /**
     * 将业务及异常处理固定到借用执行器，来源异常不泄露原文。
     * @param control 借用控制
     * @param action 本次操作
     * @param <T> 返回类型
     * @return 可停止且错误脱敏的阶段
     */
    private <T> CompletionStage<T> controlled(ExecutionControl control, Supplier<CompletionStage<T>> action) {
        try {
            return control.guard(CompletableFuture.supplyAsync(() -> { control.checkActive(); return action.get(); }, executor).thenCompose(stage -> stage))
                    .exceptionallyCompose(failure -> {
                        while (failure instanceof CompletionException && failure.getCause() != null) { failure = failure.getCause(); }
                        RuntimeException safe = switch (failure) {
                            case ExecutionStoppedException stopped -> new ExecutionStoppedException(stopped.reason());
                            case AgentPromptException known -> new AgentPromptException(known.reason());
                            case AgentMemoryReferenceException known -> new AgentMemoryReferenceException(known.reason());
                            default -> new AgentPromptException(AgentPromptException.Reason.UNAVAILABLE);
                        };
                        return CompletableFuture.failedStage(safe);
                    });
        } catch (RuntimeException ignored) { return CompletableFuture.failedStage(new AgentPromptException(AgentPromptException.Reason.UNAVAILABLE)); }
    }
    /**
     * 启用、采集和查询分开，不控制长期记忆或人格学习。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param enabled 真实登记且启用
     * @param capture 是否采集
     * @param read 是否允许查询
     * @author zExNocs
     * @date 2026/10/30
     * @since 4.5.0-l
     * @version 4.5.0-l
     */
    public record Policy(boolean enabled, boolean capture, boolean read) {}
}
