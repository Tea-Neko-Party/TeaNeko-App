package org.zexnocs.teanekoagent.personality;

import org.zexnocs.teanekoagent.agent.AgentScope;
import org.zexnocs.teanekoagent.agent.personality.AgentPersonalitySnapshot;
import org.zexnocs.teanekoagent.agent.personality.IAgentPersonalitySource;
import org.zexnocs.teanekoagent.agent.prompt.AgentPromptException;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.actuator.execution.ExecutionStoppedException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.SerializationFeature;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * 组合人格选择、独立补正和持久化引用；不生成用户画像、不调用模型或自动提升学习文本。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public final class PersonalityService implements IAgentPersonalitySource {
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
                return new AgentPersonalitySnapshot(binding.identity(settings.correctionsEnabled()), base.id(), base.version(), state.key(), state.revision(),
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
            var settings = configuration.settings(scope); var base = configuration.base(settings);
            return store.binding(scope).thenAcceptAsync(value -> {
                control.checkActive(); var expiry = snapshot.expiresAt();
                var binding = value.orElseThrow(() -> new AgentPromptException(AgentPromptException.Reason.CHANGED));
                if (!binding.baseFingerprint().equals(base.fingerprint()) || !binding.identity(settings.correctionsEnabled()).equals(snapshot.identity())
                        || expiry != null && !expiry.isAfter(clock.instant())) { throw new AgentPromptException(AgentPromptException.Reason.CHANGED); }
            }, executor);
        });
    }
    /**
     * 可信互动入口提交表达候选，学习开关、来源、版本及字段均在实际写入前检查。
     * 当前不自动调用模型产生候选；锁定或其他管理操作留给独立 DEBUG 入口。
     * @param scope 真实互动作用域
     * @param basis 产生候选时实际使用的人格快照，旧绑定不得刷新成新引用
     * @param candidate 宿主固定来源候选
     * @param control 本阶段借用控制
     * @return 真实提交后是否新增；原候选重复返回 false
     */
    public CompletionStage<Boolean> propose(AgentScope scope, AgentPersonalitySnapshot basis, PersonalityCorrection candidate, ExecutionControl control) {
        return controlled(control, () -> {
            var settings = configuration.settings(scope); var base = configuration.base(settings);
            if (!settings.learningEnabled()) { throw new AgentPromptException(AgentPromptException.Reason.DISABLED); }
            if (!scope.equals(candidate.source().scope()) || !base.id().equals(candidate.baseId()) || base.version() != candidate.baseVersion()
                    || !base.mutableFields().contains(candidate.field()) || candidate.confidence() < 0.2 || candidate.locked() || !candidate.activeAt(clock.instant())) {
                throw new IllegalArgumentException("人格补正来源、版本、字段或置信度不被接受。");
            }
            return store.binding(scope).thenComposeAsync(value -> {
                control.checkActive(); var binding = value.orElseThrow(() -> new AgentPromptException(AgentPromptException.Reason.CHANGED));
                if (!binding.baseFingerprint().equals(base.fingerprint()) || !binding.identity(settings.correctionsEnabled()).equals(basis.identity())) {
                    throw new AgentPromptException(AgentPromptException.Reason.CHANGED);
                }
                return store.append(binding, candidate);
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
        if (failure instanceof AgentPromptException known) { return new AgentPromptException(known.reason()); }
        return new AgentPromptException(AgentPromptException.Reason.UNAVAILABLE);
    }
}
