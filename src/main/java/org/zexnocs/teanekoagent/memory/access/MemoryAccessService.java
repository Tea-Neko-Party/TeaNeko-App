package org.zexnocs.teanekoagent.memory.access;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.agent.memory.AgentMemoryReferenceException;
import org.zexnocs.teanekoagent.agent.memory.IAgentMemoryReferences;
import org.zexnocs.teanekoagent.memory.fact.*;
import org.zexnocs.teanekoagent.memory.profile.IUserProfileService;
import org.zexnocs.teanekoagent.memory.profile.UserProfileRequest;
import org.zexnocs.teanekoagent.memory.profile.UserProfileView;
import org.zexnocs.teanekoagent.memory.recall.*;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.actuator.execution.ExecutionStoppedException;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * 组合可信身份、动态开关、引用复核及事实业务，不授予模型记忆管理或其他主体写入能力。
 * 所有策略和业务调用回到借用业务域；动作前观察不替代原存储事务中的 generation 仲裁。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public final class MemoryAccessService {
    private final IMemoryAccessPolicy policies;
    private final IAgentMemoryReferences references;
    private final IMemoryRecallService recall;
    private final IUserProfileService profiles;
    private final MemoryFacts facts;
    private final Executor executor;

    /**
     * 保存可替换业务端口，构造不读取配置、数据库或创建执行器。
     * @param policies 当前作用域记忆策略
     * @param references 原引用只读校验端口
     * @param recall 确定性检索端口
     * @param profiles 可追溯画像端口
     * @param facts 真实提交后完成的追加服务
     * @param executor Core 借用业务执行器，本服务不关闭它
     * @throws NullPointerException 当依赖为空时
     */
    public MemoryAccessService(IMemoryAccessPolicy policies, IAgentMemoryReferences references, IMemoryRecallService recall,
            IUserProfileService profiles, MemoryFacts facts, Executor executor) {
        this.policies = Objects.requireNonNull(policies, "记忆访问策略端口不能为空。");
        this.references = Objects.requireNonNull(references, "记忆引用校验端口不能为空。");
        this.recall = Objects.requireNonNull(recall, "记忆检索端口不能为空。");
        this.profiles = Objects.requireNonNull(profiles, "记忆画像端口不能为空。");
        this.facts = Objects.requireNonNull(facts, "记忆追加服务不能为空。");
        this.executor = Objects.requireNonNull(executor, "记忆业务执行器不能为空。");
    }

    /**
     * 在当前固定空间共享检索，不把发起者变成隐式主体过滤；交付前再次复核开关与引用。
     * @param context 宿主固定身份及空间
     * @param query 不包含授权字段的有界业务查询
     * @param use 注入或工具预算用途
     * @param limits 可选收紧预算
     * @param control 本阶段借用控制，不在此关闭
     * @return 保留覆盖程度与来源的证据，失败不当作正常空结果
     * @throws NullPointerException 当必需参数为空时
     */
    public CompletionStage<MemoryRecallResult> recall(MemoryAccessContext context, MemoryRecallQuery query,
            MemoryRecallRequest.Use use, @Nullable MemoryRecallLimits limits, ExecutionControl control) {
        var request = new MemoryRecallRequest(context.reference(), query, use, limits);
        return execute(context, false, control, () -> recall.recall(request).thenApplyAsync(result -> {
            Objects.requireNonNull(result, "记忆检索结果不能为空。");
            if (!result.reference().equals(context.reference()) || exceeds(limits, result.evidence().size(), result.characters(), result.scanned())) {
                throw new MemoryAccessException(MemoryAccessException.Reason.UNAVAILABLE);
            }
            return result;
        }, executor));
    }

    /**
     * 同空间可明确查询其他主体的画像，查询主体不会变成追加权限。
     * @param context 宿主固定身份及空间
     * @param subjectId 明确查询的 TeaNeko UUID
     * @param use 注入或工具预算用途
     * @param limits 可选收紧预算
     * @param control 本阶段借用控制
     * @return 保留原证据和覆盖信息的画像
     * @throws NullPointerException 当必需参数为空时
     */
    public CompletionStage<UserProfileView> profile(MemoryAccessContext context, UUID subjectId,
            MemoryRecallRequest.Use use, @Nullable MemoryRecallLimits limits, ExecutionControl control) {
        var request = new UserProfileRequest(context.reference(), subjectId, use, limits);
        return execute(context, false, control, () -> profiles.read(request).thenApplyAsync(view -> {
            Objects.requireNonNull(view, "记忆画像结果不能为空。");
            if (!view.reference().equals(context.reference()) || !view.subjectId().equals(subjectId)
                    || exceeds(limits, view.evidence().size(), view.characters(), view.scanned())) {
                throw new MemoryAccessException(MemoryAccessException.Reason.UNAVAILABLE);
            }
            return view;
        }, executor));
    }

    /**
     * 只读复核当前读取开关和原完整引用，不检索资料、追加事实或创建模型预算。
     * 供资料实际使用及重放前调用，借用控制的停止不会撤销已开始的存储操作。
     * @param context 原可信上下文
     * @param control 本次使用或观察的借用控制
     * @return 当前仍允许使用原空间资料时完成
     * @throws NullPointerException 当必需参数为空时
     */
    public CompletionStage<Void> verifyRead(MemoryAccessContext context, ExecutionControl control) {
        Objects.requireNonNull(context, "记忆复核上下文不能为空。"); Objects.requireNonNull(control, "记忆复核控制不能为空。");
        try {
            var checked = CompletableFuture.runAsync(() -> check(context, false, control), executor)
                    .thenComposeAsync(ignored -> {
                        control.checkActive(); return control.guard(Objects.requireNonNull(references.verify(context.reference()), "读取复核阶段不能为空。"));
                    }, executor).thenRunAsync(() -> check(context, false, control), executor);
            return control.guard(checked).handle((ignored, failure) -> { if (failure != null) { throw classify(failure); } return null; });
        } catch (RuntimeException failure) { return CompletableFuture.failedStage(classify(failure)); }
    }

    /**
     * 只追加真实发起者明确提供的原文；动作标识由宿主工具调用 ID 提供，不放入模型参数。
     * 成功等待原事务提交；停止或换空间后的失败不声称撤销已经开始的数据库工作。
     * @param context 宿主固定身份及空间
     * @param actionId 宿主原动作标识，重试复用，不能来自工具业务参数
     * @param intent 从当前原消息识别的意图，缺失时拒绝
     * @param value 已校验内容，正文必须与意图原文完全相同
     * @param control 本阶段借用控制
     * @return 不含原审计正文的提交回执
     * @throws NullPointerException 当必需参数为空时
     */
    public CompletionStage<MemoryAddResult> append(MemoryAccessContext context, String actionId, @Nullable MemoryAddIntent intent,
            MemoryFactValue value, ExecutionControl control) {
        Objects.requireNonNull(value, "明确新增内容不能为空。");
        return execute(context, true, control, () -> {
            if (intent == null || !intent.permits(context, value.text())) { throw new MemoryAccessException(MemoryAccessException.Reason.INTENT_REQUIRED); }
            var subject = context.actor().userId().orElseThrow(() -> new MemoryAccessException(MemoryAccessException.Reason.INTENT_REQUIRED));
            var source = new MemoryFactSource(context.requestId(), actionId, context.actor());
            var operationId = UUID.nameUUIDFromBytes(("agent-memory-add-v1:" + context.requestId() + ":" + source.actionId()).getBytes(StandardCharsets.UTF_8));
            var command = new MemoryFactAppend(operationId, context.reference(), subject, value, source);
            return facts.append(command).thenApplyAsync(receipt -> {
                Objects.requireNonNull(receipt, "明确新增回执不能为空。");
                var record = receipt.record();
                if (record != null && (!record.namespaceId().equals(context.reference().namespaceId())
                        || record.generation() != context.reference().generation() || !record.subjectId().equals(subject) || !record.value().equals(value))) {
                    throw new MemoryAccessException(MemoryAccessException.Reason.UNAVAILABLE);
                }
                return new MemoryAddResult(receipt.status(), record == null ? null : record.id());
            }, executor);
        });
    }

    /**
     * 每次等待完成后重新进入业务域，检查停止、当前开关及原引用，不在控制锁内执行数据库调用。
     * 取消观察 Future 不取消已开始的事务；只有借用 control 的终止才停止后续动作和交付。
     * @param context 原始可信上下文
     * @param add 是否属于明确新增
     * @param control 借用控制
     * @param operation 已授权后才能启动的业务
     * @param <T> 不可变业务结果类型
     * @return 经交付前复核和固定错误分类的观察阶段
     * @throws NullPointerException 当上下文或控制为空时
     */
    private <T> CompletionStage<T> execute(MemoryAccessContext context, boolean add, ExecutionControl control, Supplier<CompletionStage<T>> operation) {
        Objects.requireNonNull(context, "记忆访问上下文不能为空。"); Objects.requireNonNull(control, "记忆访问执行控制不能为空。");
        try {
            var started = CompletableFuture.runAsync(() -> check(context, add, control), executor)
                    .thenComposeAsync(ignored -> control.guard(Objects.requireNonNull(references.verify(context.reference()), "记忆引用校验阶段不能为空。")), executor)
                    .thenComposeAsync(ignored -> {
                        check(context, add, control);
                        return control.guard(Objects.requireNonNull(operation.get(), "记忆业务阶段不能为空。"));
                    }, executor)
                    .thenComposeAsync(value -> {
                        check(context, add, control);
                        return control.guard(Objects.requireNonNull(references.verify(context.reference()), "记忆交付校验阶段不能为空。"))
                                .thenApplyAsync(ignored -> { check(context, add, control); return value; }, executor);
                    }, executor);
            return control.guard(started).handle((value, failure) -> {
                if (failure != null) { throw classify(failure); } return value;
            });
        } catch (RuntimeException failure) { return CompletableFuture.failedStage(classify(failure)); }
    }

    /**
     * 在业务域读取独立开关，学习开关不能绕过明确新增意图或授予管理权限。
     * @param context 原上下文
     * @param add 是否检查新增开关
     * @param control 借用控制
     * @throws MemoryAccessException 当功能关闭或策略读取失败时
     * @throws ExecutionStoppedException 当控制已经停止时
     */
    private void check(MemoryAccessContext context, boolean add, ExecutionControl control) {
        control.checkActive();
        final MemoryAccessPolicy policy;
        try { policy = Objects.requireNonNull(policies.read(context.actor().scope()), "记忆访问策略不能为空。"); }
        catch (RuntimeException ignored) { throw new MemoryAccessException(MemoryAccessException.Reason.CONFIGURATION_ERROR); }
        control.checkActive();
        if (!policy.enabled()) { throw new MemoryAccessException(MemoryAccessException.Reason.DISABLED); }
        if (add && !policy.addEnabled()) { throw new MemoryAccessException(MemoryAccessException.Reason.ADD_DISABLED); }
        if (!add && !policy.readEnabled()) { throw new MemoryAccessException(MemoryAccessException.Reason.READ_DISABLED); }
    }

    /**
     * 复核可替换来源遵守宿主显式预算，不重新度量或扩大原查询预算。
     * @param limits 原显式预算，空时由底层本地策略约束
     * @param results 实际结果数
     * @param characters 原证据数组字符数
     * @param scanned 实际检查数
     * @return 来源超出任一显式预算时为 true
     */
    private static boolean exceeds(@Nullable MemoryRecallLimits limits, int results, int characters, int scanned) {
        return limits != null && (results > limits.maxResults() || characters > limits.maxCharacters() || scanned > limits.maxScanned());
    }

    /**
     * 重建已知固定错误以移除原文、异常链和 suppressed；其他失败不伪装无记忆。
     * @param failure 原始阶段错误
     * @return 安全的固定分类异常
     */
    private static RuntimeException classify(Throwable failure) {
        var current = failure;
        for (int depth = 0; depth < 32 && (current instanceof CompletionException || current instanceof ExecutionException); depth++) {
            var cause = current.getCause(); if (cause == null || cause == current) { break; } current = cause;
        }
        if (current instanceof MemoryAccessException known) { return new MemoryAccessException(known.reason()); }
        if (current instanceof AgentMemoryReferenceException known) { return new AgentMemoryReferenceException(known.reason()); }
        if (current instanceof MemoryFactGenerationException known) { return new MemoryFactGenerationException(known.missingNamespace()); }
        if (current instanceof MemoryRecallException known) { return new MemoryRecallException(known.reason()); }
        if (current instanceof ExecutionStoppedException stopped) {
            return new MemoryAccessException(stopped.reason() == ExecutionControl.StopReason.DEADLINE_EXCEEDED
                    ? MemoryAccessException.Reason.TIMED_OUT : MemoryAccessException.Reason.STOPPED);
        }
        return new MemoryAccessException(MemoryAccessException.Reason.UNAVAILABLE);
    }
}
