package org.zexnocs.teanekoplugin.agent.cognition.personality;

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。


// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekoagent.runtime.prompt.AgentPromptException;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.actuator.execution.ExecutionStoppedException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.SerializationFeature;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * 组合人格选择、独立补正和持久化引用；不生成用户画像、不调用模型或自动提升学习文本。
 * <br>2026/11/03：自动学习只接收原用户来源，独立管理来源经专用管理事务处理。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：未安装人格时不初始化存储或接受学习候选。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：简化冗余类型实参、变量或转换，保持原业务与测试边界。
 * <br>2026/11/06：保留人格存储边界的安全根因定位。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 *
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
@org.zexnocs.teanekocore.plugin.PluginOwned("agent.cognition")
public final class PersonalityService implements IAgentPersonalitySource, IPersonalityLearningTarget {
    private final IPersonalityConfiguration configuration;
    private final IPersonalityStore store;
    private final Executor executor;
    private final Clock clock;
    private final ObjectWriter writer;
    /**
     * 保存业务端口，构造无 I/O，执行器及控制均由外部拥有。
     * @param configuration 真实配置端口
     * @param store 独立人格事务端口
     * @param executor 借用业务执行器
     * @param clock 有效期时钟
     * @param mapper JSON 转换器
     */
    public PersonalityService(IPersonalityConfiguration configuration, IPersonalityStore store, Executor executor, Clock clock, ObjectMapper mapper) {
        this.configuration = Objects.requireNonNull(configuration, "人格配置端口不能为空。"); this.store = Objects.requireNonNull(store, "人格存储端口不能为空。");
        this.executor = Objects.requireNonNull(executor, "人格业务执行器不能为空。"); this.clock = Objects.requireNonNull(clock, "人格时钟不能为空。");
        writer = mapper.writer().without(SerializationFeature.INDENT_OUTPUT, SerializationFeature.WRAP_ROOT_VALUE);
    }
    /**
     * 取得本轮快照，基础变化的备份和新绑定须提交后才允许进入模型。
     * @param scope 真实作用域
     * @param control 借用执行控制
     * @return 冻结人格与数据补正
     */
    @Override public CompletionStage<AgentPersonalitySnapshot> resolve(AgentScope scope, ExecutionControl control) {
        return controlled(control, () -> {
            // 2026/11/04：无人格组件时只提供记忆分区标记，不读取基础人格或补正库。
            if (!configuration.installed(scope)) {
                return CompletableFuture.completedStage(AgentPersonalitySnapshot.absent(scope));
            }
            var settings = configuration.settings(scope); var base = configuration.base(settings);
            return store.resolve(scope, base, clock.instant()).thenApplyAsync(selection -> {
                control.checkActive(); var now = clock.instant(); var binding = selection.binding(); var state = selection.state();
                if (!binding.scope().equals(scope) || !binding.baseFingerprint().equals(base.fingerprint()) || !binding.correctionKey().equals(state.key())) {
                    throw new AgentPromptException(AgentPromptException.Reason.UNAVAILABLE);
                }
                var notes = settings.correctionsEnabled() ? state.corrections().stream().filter(item -> item.baseId().equals(base.id())
                        && item.baseVersion() == base.version() && base.mutableFields().contains(item.field()) && item.confidence() >= 0.2
                        && item.activeAt(now)).toList() : List.<PersonalityCorrection>of();
                var expiry = notes.stream().map(PersonalityCorrection::expiresAt).filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
                return new AgentPersonalitySnapshot(binding.identity(settings.correctionsEnabled(), state.managementRevision()), base.id(), base.version(), state.key(), state.revision(),
                        base.instructions(), notes.isEmpty() ? "" : writer.writeValueAsString(notes), expiry);
            }, executor);
        });
    }
    /**
     * 校验配置内容、绑定和本轮已采用补正的有效期，不刷新当前快照或因新候选中断同轮。
     * @param scope 原作用域
     * @param snapshot 原人格
     * @param control 当前使用/观察控制
     * @return 原身份仍成立时完成
     */
    @Override public CompletionStage<Void> verify(AgentScope scope, AgentPersonalitySnapshot snapshot, ExecutionControl control) {
        return controlled(control, () -> {
            // 2026/11/04：人格有无切换同样使旧快照失效，缺省模式不查询人格存储。
            boolean installed = configuration.installed(scope);
            if (installed != snapshot.present()) {
                throw new AgentPromptException(AgentPromptException.Reason.CHANGED);
            }
            if (!installed) {
                return CompletableFuture.completedStage(null);
            }
            var settings = configuration.settings(scope); var base = configuration.base(settings);
            return store.binding(scope).thenComposeAsync(value -> {
                control.checkActive(); var expiry = snapshot.expiresAt();
                var binding = value.orElseThrow(() -> new AgentPromptException(AgentPromptException.Reason.CHANGED));
                return store.state(binding.correctionKey()).thenAccept(state -> {
                    control.checkActive(); var current = state.orElseThrow(() -> new AgentPromptException(AgentPromptException.Reason.CHANGED));
                    if (!binding.baseFingerprint().equals(base.fingerprint()) || !binding.identity(settings.correctionsEnabled(), current.managementRevision()).equals(snapshot.identity())
                            || expiry != null && !expiry.isAfter(clock.instant())) { throw new AgentPromptException(AgentPromptException.Reason.CHANGED); }
                });
            }, executor);
        });
    }
    /**
     * 取得原人格共同白名单；学习关闭不影响已有补正的独立应用。
     * 2026/11/05：简化冗余类型实参、变量或转换，保持原业务与测试边界。
     * @param scope 原作用域
     * @param basis 原互动依据
     * @param control 借用执行控制
     * @return 仍允许学习的字段，关闭时为空
     */
    @Override public CompletionStage<Set<String>> learningFields(AgentScope scope, AgentPersonalitySnapshot basis, ExecutionControl control) {
        return controlled(control, () -> verify(scope, basis, control).thenApplyAsync(ignored -> {
            // 2026/11/04：无人格快照不访问基础设定，也不生成学习候选。
            if (!basis.present()) {
                return Set.of();
            }
            control.checkActive(); var settings = configuration.settings(scope);
            return settings.learningEnabled() ? configuration.base(settings).mutableFields() : Set.of();
        }, executor));
    }
    /**
     * 可信互动入口提交表达候选，学习开关、来源、版本及字段均在实际写入前检查。
     * 模型生成由独立后台策略负责；锁定或其他管理操作留给独立 DEBUG 入口。
     * @param scope 真实互动作用域
     * @param basis 产生候选时实际使用的人格快照，旧绑定不得刷新成新引用
     * @param candidate 宿主固定来源候选
     * @param control 本阶段借用控制
     * @return 真实提交后是否新增；原候选重复返回 false
     */
    @Override public CompletionStage<Boolean> propose(AgentScope scope, AgentPersonalitySnapshot basis, PersonalityCorrection candidate, ExecutionControl control) {
        return controlled(control, () -> {
            // 2026/11/04：独立调用学习入口也必须安装人格，不能从保留的历史绑定恢复能力。
            if (!basis.present() || !configuration.installed(scope)) {
                throw new AgentPromptException(AgentPromptException.Reason.DISABLED);
            }
            var settings = configuration.settings(scope); var base = configuration.base(settings);
            if (!settings.learningEnabled()) { throw new AgentPromptException(AgentPromptException.Reason.DISABLED); }
            // 2026/11/03：扩大管理来源后保持原自动学习入口的用户边界。
            if (candidate.source().origin() != org.zexnocs.teanekoagent.runtime.AgentActorContext.Origin.USER
                    || !scope.equals(candidate.source().scope()) || !base.id().equals(candidate.baseId()) || base.version() != candidate.baseVersion()
                    || !base.mutableFields().contains(candidate.field()) || candidate.confidence() < 0.2 || candidate.locked() || !candidate.activeAt(clock.instant())) {
                throw new IllegalArgumentException("人格补正来源、版本、字段或置信度不被接受。");
            }
            return store.binding(scope).thenComposeAsync(value -> {
                control.checkActive(); var binding = value.orElseThrow(() -> new AgentPromptException(AgentPromptException.Reason.CHANGED));
                return store.state(binding.correctionKey()).thenCompose(state -> {
                    var current = state.orElseThrow(() -> new AgentPromptException(AgentPromptException.Reason.CHANGED));
                    if (!binding.baseFingerprint().equals(base.fingerprint()) || !binding.identity(settings.correctionsEnabled(), current.managementRevision()).equals(basis.identity())) {
                        throw new AgentPromptException(AgentPromptException.Reason.CHANGED);
                    }
                    return store.append(binding, candidate, current.managementRevision(), () -> {
                        control.checkActive(); var latest = configuration.settings(scope); var actual = configuration.base(latest);
                        if (!configuration.installed(scope) || !latest.learningEnabled() || !actual.fingerprint().equals(base.fingerprint()) || !candidate.activeAt(clock.instant())
                                || latest.correctionsEnabled() != settings.correctionsEnabled()) { throw new AgentPromptException(AgentPromptException.Reason.CHANGED); }
                    });
                });
            }, executor).thenComposeAsync(stored -> {
                control.checkActive();
                if (!configuration.settings(scope).learningEnabled()) { throw new AgentPromptException(AgentPromptException.Reason.DISABLED); }
                return verify(scope, basis, control).thenApply(ignored -> stored);
            }, executor);
        });
    }
    /**
     * 切入业务域并观察停止，来源失败不携带配置、候选正文或原异常链。
     * @param control 借用控制
     * @param action 本次业务操作
     * @param <T> 结果类型
     * @return 安全分类的阶段
     */
    private <T> CompletionStage<T> controlled(ExecutionControl control, Supplier<CompletionStage<T>> action) {
        try {
            var work = CompletableFuture.supplyAsync(() -> { control.checkActive(); return Objects.requireNonNull(action.get(), "人格操作阶段不能为空。"); }, executor)
                    .thenCompose(stage -> stage);
            return control.guard(work).handle((value, failure) -> { if (failure != null) { throw safe(failure); } return value; });
        } catch (RuntimeException failure) { return CompletableFuture.failedStage(safe(failure)); }
    }
    /**
     * 保留停止及换代类别，其他错误不能伪装成没有人格。
     * @param failure 原始阶段异常
     * @return 固定非敏感异常
     */
    private RuntimeException safe(Throwable failure) {
        for (int depth = 0; depth < 32 && (failure instanceof CompletionException || failure instanceof ExecutionException); depth++) {
            var cause = failure.getCause(); if (cause == null || cause == failure) { break; } failure = cause;
        }
        if (failure instanceof ExecutionStoppedException known) { return new ExecutionStoppedException(known.reason()); }
        if (failure instanceof AgentPromptException known) { return new AgentPromptException(known.reason(), known); }
        return new AgentPromptException(AgentPromptException.Reason.UNAVAILABLE, failure);
    }
}
