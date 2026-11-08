package org.zexnocs.teanekoplugin.agent.cognition.integration.message;

import org.zexnocs.teanekoagent.integration.config.AgentComponentAccess;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

import org.zexnocs.teanekoagent.integration.message.IPlatformHistoryCapture;
import org.zexnocs.teanekoagent.integration.message.NativeMessageInput;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import org.zexnocs.teanekocore.plugin.IPluginLifecycle;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import org.zexnocs.teanekoplugin.support.PluginModelScope;
import org.springframework.beans.factory.annotation.Autowired;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoplugin.agent.cognition.memory.history.HistoryContext;
import org.zexnocs.teanekoplugin.agent.cognition.memory.history.HistoryMessage;
import org.zexnocs.teanekoplugin.agent.cognition.memory.history.IConversationHistory;
import org.zexnocs.teanekoplugin.agent.cognition.memory.runtime.IAgentMemoryReferences;
import org.zexnocs.teanekoplugin.agent.cognition.personality.IAgentPersonalitySource;
import org.zexnocs.teanekoagent.runtime.operation.AgentOperationReceipt;
import org.zexnocs.teanekoagent.runtime.operation.IAgentOperationStore;
import org.zexnocs.teanekoagent.integration.message.NativeMessageSources;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.actuator.execution.ResourceBudget;
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
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * <br>2026/11/07：由认知插件拥有采集控制与排空，在写入前复核启用代次。
 *
 * <br>2026/11/07：业务参数由所属组件解释，实时复核装配与插件资格。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
@Service
@PluginOwned("agent.cognition")
public final class PlatformHistoryCapture implements IPlatformHistoryCapture, IPluginLifecycle {
    private final PluginModelScope work = new PluginModelScope();
    private @Nullable PluginRegistry plugins;
    private final AgentComponentAccess components;
    private final IAgentMemoryReferences references;
    private final IAgentPersonalitySource personalities;
    private final IConversationHistory history;
    private final IAgentOperationStore attempts;
    private final ScheduledExecutorService scheduler;

    /**
     * 借用原历史、引用、权限、持久化防重与截止时间设施。
     * @param components 当前有效组件参数
     * @param references 原空间边界
     * @param personalities 原人格边界
     * @param history 原历史服务
     * @param attempts 原持久化防重
     * @param scheduler 借用时限设施
     */
    public PlatformHistoryCapture(AgentComponentAccess components, IAgentMemoryReferences references,
            IAgentPersonalitySource personalities, IConversationHistory history, IAgentOperationStore attempts,
            @Qualifier("taskExecutor") ScheduledExecutorService scheduler) {
        this.components = components;
        this.references = references;
        this.personalities = personalities;
        this.history = history;
        this.attempts = attempts;
        this.scheduler = scheduler;
    }

    /**
     * 安装运行期插件代次校验，独立测试宿主可不提供注册表。
     * @param plugins 真实插件目录
     */
    @Autowired(required = false)
    public void installPlugins(PluginRegistry plugins) {
        this.plugins = plugins;
    }

    /** @return 本采集服务所属插件。 */
    @Override
    public String pluginId() {
        return "agent.cognition";
    }

    /** @return 本插件内唯一生命周期标识。 */
    @Override
    public String id() {
        return "platform-history-capture";
    }

    /** @return 开放新的采集代次后的完成阶段。 */
    @Override
    public CompletionStage<Void> start() {
        work.start();
        return CompletableFuture.completedStage(null);
    }

    /** @return 取消自有控制并等待已开始的真实采集链结束。 */
    @Override
    public CompletionStage<Void> stop() {
        return work.stop(() -> { });
    }

    /**
     * 先固定当前引用和人格，提交采集后才允许自动轮次消费同一边界。
     * @param data 原生来源
     * @param input 当前冻结图文，历史仅保存字面文本
     * @return 原采集边界；重复、冲突或此前结果不明时拒绝后续自动操作
     */
    @Override
    public CompletionStage<Capture> capture(ITeaNekoMessageData data, NativeMessageInput input) {
        if (!work.active() || plugins != null && !plugins.isEnabled(pluginId())) {
            return CompletableFuture.completedStage(new Capture(null, true));
        }
        var clock = Clock.systemUTC();
        var control = ExecutionControl.withDeadline(new ResourceBudget(Map.of()),
                clock.instant().plusSeconds(30), clock, scheduler);
        try {
            var token = plugins == null ? null : plugins.capture(pluginId());
            return work.execute(control, child -> captureActive(data, input, child, token))
                    .whenComplete((result, failure) -> control.close());
        } catch (RuntimeException failure) {
            control.close();
            return CompletableFuture.failedStage(failure);
        }
    }

    /**
     * 在固定启用代次中完成去重、写入和回执，异步边界后复核插件与控制。
     * @param data 原生来源
     * @param input 已冻结输入
     * @param control 自有子控制
     * @param token 插件及传递依赖的代次
     * @return 真实采集完成阶段
     */
    private CompletionStage<Capture> captureActive(ITeaNekoMessageData data, NativeMessageInput input,
            ExecutionControl control, PluginRegistry.@Nullable Token token) {
        check(control, token);
        var actor = NativeMessageSources.actor(data);
        var parameters = components.settings(actor.scope(), "memory").orElse(null);
        if (parameters == null || !parameters.path("historyCaptureEnabled").asBoolean(true)
                || input.text().isBlank()) {
            return CompletableFuture.completedStage(new Capture(null, parameters != null));
        }
        var id = UUID.nameUUIDFromBytes(StableDigest.sha256("native-capture-v1", actor.scope().clientId(),
                actor.scope().scopeId(), actor.sourceId()).getBytes(StandardCharsets.UTF_8));
        return references.resolve(actor.scope()).thenCompose(reference -> {
            check(control, token);
            return personalities.resolve(actor.scope(), control).thenCompose(personality -> {
                check(control, token);
                var context = new HistoryContext(reference, personality);
                var attempt = new IAgentOperationStore.Attempt(id, "collect-entry", actor, reference,
                        input.digest(), IAgentOperationStore.Kind.COLLECT_ENTRY);
                return attempts.claim(attempt).thenCompose(claim -> {
                    check(control, token);
                    if (!claim.acquired()) {
                        return CompletableFuture.completedStage(new Capture(context, false));
                    }
                    var message = new HistoryMessage(actor.sourceId(), actor, HistoryMessage.Role.USER,
                            input.text(), input.occurredAt(), false, false);
                    return history.append(context, message, control)
                            .handle((added, failure) -> attempts.finish(attempt, new AgentOperationReceipt(
                                    failure == null ? AgentOperationReceipt.Status.COMPLETED
                                            : AgentOperationReceipt.Status.FAILED))
                                    .thenApply(receipt -> {
                                        if (failure != null) {
                                            throw new CompletionException(failure);
                                        }
                                        check(control, token);
                                        return new Capture(context, true);
                                    })).thenCompose(stage -> stage);
                });
            });
        });
    }

    /**
     * 拒绝停用前留下的控制和插件代次，重新启用不能复活旧采集。
     * @param control 自有执行控制
     * @param token 原插件代次
     */
    private void check(ExecutionControl control, PluginRegistry.@Nullable Token token) {
        control.checkActive();
        if (plugins != null && token != null) {
            plugins.check(token);
        }
    }
}
