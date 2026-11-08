package org.zexnocs.teanekoplugin.agent.cognition.memory.layered;

import org.zexnocs.teanekoplugin.agent.cognition.CognitionContext;
// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。


// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoplugin.agent.cognition.memory.runtime.IAgentMemoryReferences;
import org.zexnocs.teanekoagent.runtime.AgentActorContext;
import org.zexnocs.teanekoagent.runtime.AgentRunRequest;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.utils.StableDigest;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * 长期记忆的可信读写边界，模型不能指定 namespace 或以工具参数自报操作者。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.1-l
 * @version 4.9.0-l
 */
@org.zexnocs.teanekocore.plugin.PluginOwned("agent.cognition")
public final class LongTermMemoryAccess {
    private final ILongTermMemoryStore store;
    private final IAgentMemoryReferences references;
    private final Function<AgentScope, Policy> policies;
    private final Executor executor;
    private final Clock clock;
    /**
     * 借用原业务端口，不在构造阶段读取配置。
     * @param store 长期存储
     * @param references 原引用复核
     * @param policies 实际配置策略
     * @param executor 原业务域
     * @param clock 原时钟
     */
    public LongTermMemoryAccess(ILongTermMemoryStore store, IAgentMemoryReferences references, Function<AgentScope, Policy> policies, Executor executor, Clock clock) {
        this.store = store; this.references = references; this.policies = policies; this.executor = executor; this.clock = clock;
    }
    /**
     * 只检查实际上下文是否具备读取能力，不访问模型参数。
     * @param request 宿主固定请求
     * @return 当前允许读取时为真
     */
    public boolean canRead(AgentRunRequest request) {
        var policy = policies.apply(request.actor().scope());
        return policy.enabled() && policy.read() && CognitionContext.reference(request.memoryReference()) != null;
    }
    /**
     * 明确自然语言维护入口，含糊或否定询问不授权删除。
     * @param request 原请求
     * @param correction 是否为纠正，否则遗忘
     * @return 本人原文明确提出对应维护时为真
     */
    public boolean canChange(AgentRunRequest request, boolean correction) {
        if (!canRead(request) || !policies.apply(request.actor().scope()).write() || request.actor().origin() != AgentActorContext.Origin.USER || request.userText() == null) { return false; }
        var original = request.userText().text().stripLeading();
        var prefixes = correction ? List.of("纠正", "请纠正", "更正", "请更正") : List.of("忘记", "请忘记", "删除记忆", "请删除记忆");
        return prefixes.stream().anyMatch(original::startsWith);
    }
    /**
     * 读取前后复核固定引用，返回普通投影而非原始证据。
     * @param request 固定宿主请求
     * @param subjectId 可选用户主体
     * @param query 可选话题
     * @param limit 结果条数上限
     * @param control 借用控制
     * @return 实际查询后的有界结果
     */
    public CompletionStage<LongMemories.Result> read(AgentRunRequest request, @Nullable UUID subjectId, String query, int limit, ExecutionControl control) {
        return CompletableFuture.supplyAsync(() -> verify(request, control).thenComposeAsync(ignored ->
                store.query(Objects.requireNonNull(CognitionContext.reference(request.memoryReference())), subjectId, query, limit), executor)
                .thenComposeAsync(result -> verify(request, control).thenApply(ignored -> result), executor), executor).thenCompose(stage -> stage);
    }
    /**
     * 实际工具维护保留原调用 ID，按本人身份和原版本提交。
     * @param request 原宿主请求
     * @param actionId 已接受的工具调用 ID
     * @param id 目标记录
     * @param revision 预期版本
     * @param text 纠正正文，空值表示遗忘
     * @param control 借用控制
     * @return 实际提交回执，失败不表示已开始事务一定回滚
     */
    public CompletionStage<LongMemories.Item> change(AgentRunRequest request, String actionId, UUID id, long revision, @Nullable String text, ExecutionControl control) {
        return CompletableFuture.supplyAsync(() -> {
            if (!canChange(request, text != null)) { throw new IllegalArgumentException("当前原文没有授权本人记忆维护。"); }
            var operation = UUID.nameUUIDFromBytes(StableDigest.sha256("long-memory-change-v1", request.requestId().toString(), actionId).getBytes(StandardCharsets.UTF_8));
            return verify(request, control).thenComposeAsync(ignored -> {
                control.checkActive(); if (!canChange(request, text != null)) { throw new IllegalArgumentException("本人记忆维护资格已失效。"); }
                return store.change(Objects.requireNonNull(CognitionContext.reference(request.memoryReference())), request.actor().userId().orElseThrow(), id, revision,
                        text, operation, request.actor().sourceId(), clock.instant());
            }, executor).thenComposeAsync(result -> verify(request, control).thenApply(ignored -> result), executor);
        }, executor).thenCompose(stage -> stage);
    }
    /**
     * 复核实际采用的权威版本，拒绝已更正或遗忘的旧参考资料。
     * @param request 固定请求
     * @param items 已采用投影
     * @param control 当前控制
     * @return 校验完成阶段
     */
    public CompletionStage<Void> verifyItems(AgentRunRequest request, List<LongMemories.Item> items, ExecutionControl control) {
        return verify(request, control).thenComposeAsync(ignored -> control.guard(store.verify(
                Objects.requireNonNull(CognitionContext.reference(request.memoryReference())), items)), executor).thenAccept(valid -> {
            control.checkActive(); if (!valid) { throw new IllegalStateException("已采用的长期记忆已变更，请重新查询。"); }
        });
    }
    /**
     * 使用前复核引用和当前读取策略，不刷新为另一个空间。
     * @param request 固定请求
     * @param control 当前控制
     * @return 验证完成阶段
     */
    public CompletionStage<Void> verify(AgentRunRequest request, ExecutionControl control) {
        control.checkActive();
        if (!canRead(request)) { throw new IllegalStateException("当前作用域不能读取长期记忆。"); }
        return control.guard(references.verify(Objects.requireNonNull(CognitionContext.reference(request.memoryReference())))).thenRunAsync(() -> {
            control.checkActive(); if (!canRead(request)) { throw new IllegalStateException("长期记忆读取资格已失效。"); }
        }, executor);
    }
    /**
     * 真实作用域配置，不由模型的声明产生。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
     *
     * @param enabled 已登记且启用
     * @param read 允许读取
     * @param learn 允许自动学习
     * @param write 允许本人明确维护
     *
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.6.1-l
     * @version 4.9.0-l
     */
    public record Policy(boolean enabled, boolean read, boolean learn, boolean write) { }
}
