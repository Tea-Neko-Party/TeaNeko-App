package org.zexnocs.teanekoplugin.agent.cognition.personality;

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。


// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoplugin.agent.cognition.memory.layered.LongMemories;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;

import java.time.Clock;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * 固定观察所用人格和当前配置，将生成目标与事务内的采用守卫分开。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：移除包迁移后重复或未使用的类型导入。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.1-l
 * @version 4.9.0-l
 */
@org.zexnocs.teanekocore.plugin.PluginOwned("agent.cognition")
public final class StyleObservationService {
    private final IAgentPersonalitySource personalities;
    private final IPersonalityConfiguration configuration;
    private final IStyleObservationStore store;
    private final Function<AgentScope, Boolean> policies;
    private final Executor executor;
    private final Clock clock;
    /**
     * 保存借用端口，构造期不读取人格或数据库。
     * @param personalities 原人格源
     * @param configuration 原人格配置
     * @param store 观察存储
     * @param policies 当前 scope 与本地开关
     * @param executor 借用业务域
     * @param clock 当前时钟
     */
    public StyleObservationService(IAgentPersonalitySource personalities, IPersonalityConfiguration configuration, IStyleObservationStore store,
            Function<AgentScope, Boolean> policies, Executor executor, Clock clock) {
        this.personalities = personalities; this.configuration = configuration; this.store = store; this.policies = policies; this.executor = executor; this.clock = clock;
    }
    /**
     * 独立人格学习开关不受用户事实学习开关代替。
     * @param scope 实际作用域
     * @return 当前有观察接收能力时为真
     */
    public boolean enabled(AgentScope scope) { return personalities instanceof IPersonalityLearningTarget && Boolean.TRUE.equals(policies.apply(scope)); }
    /**
     * 取得当前人格，再筛选原分区中仍属于这个人格的真实样本。
     * @param batch 原长期整理输入
     * @param control 本批控制
     * @return 没有合格来源或关闭时为空值
     */
    public CompletionStage<StyleObservations.@Nullable Target> prepare(LongMemories.Batch batch, ExecutionControl control) {
        return CompletableFuture.supplyAsync(() -> {
            if (!enabled(batch.stream().reference().scope())) { return CompletableFuture.<StyleObservations.Target>completedStage(null); }
            return personalities.resolve(batch.stream().reference().scope(), control).thenComposeAsync(basis ->
                    ((IPersonalityLearningTarget) personalities).learningFields(batch.stream().reference().scope(), basis, control).thenComposeAsync(fields -> {
                        var allowed = fields.stream().filter(Set.of("speakingStyle", "speaking detail")::contains).collect(java.util.stream.Collectors.toSet());
                        if (allowed.isEmpty()) { return CompletableFuture.completedStage(null); }
                        return store.prepare(batch, basis, allowed, clock.instant()).thenApply(target -> target.evidenceIds().isEmpty() ? null : target);
                    }, executor), executor);
        }, executor).thenCompose(stage -> stage);
    }
    /**
     * 原事务采用前复核当前基础设定、字段白名单、学习与取消，不运行异步 I/O。
     * @param batch 原固定输入
     * @param target 生成时依据
     * @param control 原执行控制
     * @return 仅供本次同步事务使用的守卫
     */
    public Runnable guard(LongMemories.Batch batch, StyleObservations.Target target, ExecutionControl control) {
        return () -> {
            control.checkActive(); var scope = batch.stream().reference().scope();
            var settings = configuration.settings(scope); var base = configuration.base(settings);
            if (!enabled(scope) || !settings.learningEnabled() || !base.id().equals(target.basis().baseId())
                    || base.version() != target.basis().baseVersion() || !base.instructions().equals(target.basis().instructions())
                    || target.basis().expiresAt() != null && !target.basis().expiresAt().isAfter(clock.instant())
                    || !base.mutableFields().containsAll(target.fields())) { throw new IllegalStateException("表达观察的人格目标或学习资格已失效。"); }
        };
    }
}
