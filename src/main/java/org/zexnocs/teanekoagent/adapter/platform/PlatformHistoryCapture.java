package org.zexnocs.teanekoagent.adapter.platform;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.adapter.command.NativeCommandBridge;
import org.zexnocs.teanekoagent.adapter.config.AgentScopeConfigData;
import org.zexnocs.teanekoagent.adapter.config.AgentScopeConfigManager;
import org.zexnocs.teanekoagent.agent.command.CommandInvocationReceipt;
import org.zexnocs.teanekoagent.agent.command.ICommandAttemptStore;
import org.zexnocs.teanekoagent.agent.memory.IAgentMemoryReferences;
import org.zexnocs.teanekoagent.agent.personality.IAgentPersonalitySource;
import org.zexnocs.teanekoagent.history.HistoryContext;
import org.zexnocs.teanekoagent.history.HistoryMessage;
import org.zexnocs.teanekoagent.history.IConversationHistory;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.actuator.execution.ResourceBudget;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.utils.StableDigest;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;

/**
 * 原生旁观文本采集，独立于触发和模型，固定引用后不刷新成新空间或新人格重试。
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Service
public final class PlatformHistoryCapture {
    private final IConfigDataService configs;
    private final AgentScopeConfigManager manager;
    private final IAgentMemoryReferences references;
    private final IAgentPersonalitySource personalities;
    private final IConversationHistory history;
    private final ICommandAttemptStore attempts;
    private final ScheduledExecutorService scheduler;

    /**
     * 借用原历史、引用、权限、持久化防重与截止时间设施。
     * @param configs 原配置服务
     * @param manager Agent 注册
     * @param references 原空间边界
     * @param personalities 原人格边界
     * @param history 原历史服务
     * @param attempts 原持久化防重
     * @param scheduler 借用时限设施
     */
    public PlatformHistoryCapture(IConfigDataService configs, AgentScopeConfigManager manager, IAgentMemoryReferences references,
            IAgentPersonalitySource personalities, IConversationHistory history, ICommandAttemptStore attempts,
            @Qualifier("taskExecutor") ScheduledExecutorService scheduler) {
        this.configs = configs; this.manager = manager; this.references = references; this.personalities = personalities;
        this.history = history; this.attempts = attempts; this.scheduler = scheduler;
    }

    /**
     * 先固定当前引用和人格，先提交采集再允许自动轮次消费同一边界；旧消息重投不进入新空间。
     * @param data 原生来源
     * @param input 当前冻结图文，历史仅保存字面文本
     * @return 原采集边界；重复、冲突或此前结果不明时拒绝后续自动操作
     */
    public CompletionStage<Capture> capture(ITeaNekoMessageData data, NativeMessageInput input) {
        var clock = Clock.systemUTC(); var control = ExecutionControl.withDeadline(new ResourceBudget(Map.of()), clock.instant().plusSeconds(30), clock, scheduler);
        try {
            var config = configs.getConfigData(manager, AgentScopeConfigData.class, data.getScopeId()).orElse(null);
            if (config == null || !config.isEnabled() || !config.isHistoryCaptureEnabled() || input.text().isBlank()) {
                control.close(); return CompletableFuture.completedFuture(new Capture(null, config != null && config.isEnabled()));
            }
            var actor = NativeCommandBridge.actor(data);
            var id = UUID.nameUUIDFromBytes(StableDigest.sha256("native-capture-v1", actor.scope().clientId(), actor.scope().scopeId(), actor.sourceId()).getBytes(StandardCharsets.UTF_8));
            var work = control.guard(references.resolve(actor.scope())).thenCompose(reference -> control.guard(personalities.resolve(actor.scope(), control))
                    .thenCompose(personality -> {
                        var context = new HistoryContext(reference, personality);
                        var attempt = new ICommandAttemptStore.Attempt(id, "collect-entry", actor, reference, input.digest(), ICommandAttemptStore.Kind.COLLECT_ENTRY);
                        return attempts.claim(attempt).thenCompose(claim -> {
                            if (!claim.acquired()) { return CompletableFuture.completedFuture(new Capture(context, false)); }
                            var message = new HistoryMessage(actor.sourceId(), actor, HistoryMessage.Role.USER, input.text(), input.occurredAt(), false, false);
                            return history.append(context, message, control).handle((added, failure) -> attempts.finish(attempt,
                                    new CommandInvocationReceipt(failure == null ? CommandInvocationReceipt.Status.COMPLETED : CommandInvocationReceipt.Status.FAILED))
                                    .thenApply(receipt -> { if (failure != null) { throw new CompletionException(failure); } return new Capture(context, true); }))
                                    .thenCompose(stage -> stage);
                        });
                    }));
            return control.guard(work).whenComplete((result, failure) -> control.close());
        } catch (RuntimeException failure) { control.close(); return CompletableFuture.failedFuture(failure); }
    }

    /**
     * 采集后可供自动轮次复核的纯边界，不携带客户端或执行控制。
     * @param context 已固定上下文，采集关闭或纯图片时为空
     * @param accepted 是否允许该消息首次继续处理
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.0.0
     * @version 4.5.0-l
     */
    public record Capture(@Nullable HistoryContext context, boolean accepted) { }
}
