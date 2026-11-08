package org.zexnocs.teanekoplugin.agent.cognition.integration.command;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.integration.message.NativeMessageSources;
import org.zexnocs.teanekoagent.runtime.access.IAgentScopePolicyPort;
import org.zexnocs.teanekoagent.runtime.conversation.IAgentConversationControl;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.actuator.execution.ResourceBudget;
import org.zexnocs.teanekocore.command.api.CommandPermission;
import org.zexnocs.teanekocore.utils.StableDigest;
import org.zexnocs.teanekoplugin.agent.cognition.memory.namespace.IMemoryNamespaceBindings;
import org.zexnocs.teanekoplugin.agent.cognition.memory.namespace.MemoryNamespaceId;
import org.zexnocs.teanekoplugin.agent.cognition.memory.runtime.IAgentMemoryReferences;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。
// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。
// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

/**
 * /agent 与 /cfg 共用的空间切换边界，保持同一事务、身份及会话同步合同。
 * 不依赖命令分发器或默认 Agent 实例，配置读取不会因此创建模型入口。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
 *
 *
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
@Service
@org.zexnocs.teanekocore.plugin.PluginOwned("agent.cognition")
public final class AgentNamespaceCommands {
    private final IAgentScopePolicyPort scopes;
    private final IAgentMemoryReferences references;
    private final IMemoryNamespaceBindings bindings;
    private final IAgentConversationControl conversations;
    private final ScheduledExecutorService scheduler;
    private final org.zexnocs.teanekoplugin.agent.cognition.memory.namespace.IMemoryNamespaceRegistry namespaces;
    private final org.zexnocs.teanekoplugin.agent.cognition.memory.namespace.IMemoryNamespaceDefaults defaults;

    /**
     * 保存共享业务端口，不在装配时读取存储。
     * @param scopes 当前启用策略
     * @param references 权威运行引用
     * @param bindings 原子空间绑定
     * @param conversations 跨实例会话协调
     * @param scheduler 现有截止时间调度器
     * @param namespaces 空间注册和分页端口
     * @param defaults 本地创建配额
     */
    public AgentNamespaceCommands(IAgentScopePolicyPort scopes, IAgentMemoryReferences references,
            IMemoryNamespaceBindings bindings, IAgentConversationControl conversations, @Qualifier("taskExecutor") ScheduledExecutorService scheduler,
            org.zexnocs.teanekoplugin.agent.cognition.memory.namespace.IMemoryNamespaceRegistry namespaces,
            org.zexnocs.teanekoplugin.agent.cognition.memory.namespace.IMemoryNamespaceDefaults defaults) {
        this.scopes = scopes; this.references = references; this.bindings = bindings;
        this.conversations = conversations; this.scheduler = scheduler;
        this.namespaces = namespaces;
        this.defaults = defaults;
    }

    /**
     * 创建新空间或绑定已有空间；实际提交后的同步失败不能描述为回滚。
     * @param data 原生可信来源
     * @param target 已有空间，null 表示创建空空间
     * @param parent 可选借用父控制
     * @return 包含真实提交状态的反馈
     */
    public CompletionStage<String> change(ITeaNekoMessageData data, @Nullable String target, @Nullable ExecutionControl parent) {
        try {
            var actor = NativeMessageSources.actor(data);
            if (data.getUserData().getRole().getLevel() > CommandPermission.OWNER.getLevel()) {
                return CompletableFuture.completedStage("只有当前作用域 OWNER 或 DEBUG 可以切换空间。");
            }
            if (!scopes.read(actor.scope()).enabled()) { return CompletableFuture.completedStage("当前作用域 Agent 未登记或未启用。"); }
            var control = parent == null ? ExecutionControl.withDeadline(new ResourceBudget(Map.of()),
                    Clock.systemUTC().instant().plusSeconds(30), Clock.systemUTC(), scheduler) : parent;
            try {
                control.checkActive();
                var operation = UUID.nameUUIDFromBytes(StableDigest.sha256(data.getClient().getClientId(), data.getScopeId(),
                        data.getMessageId(), target == null ? "reset" : "setnamespace").getBytes(StandardCharsets.UTF_8));
                var work = control.guard(references.resolve(actor.scope())).thenCompose(reference -> {
                    control.checkActive();
                    if (!scopes.read(actor.scope()).enabled()) { throw new IllegalStateException("当前作用域 Agent 已关闭。"); }
                    return control.guard(target == null ? bindings.createAndBind(operation, actor, reference.bindingRevision())
                            : bindings.bindExisting(operation, actor, new MemoryNamespaceId(target), reference.bindingRevision()));
                }).thenCompose(outcome -> {
                    if (outcome.reference() == null) { return CompletableFuture.completedStage("空间操作未提交：" + outcome.status() + "；原数据保留。"); }
                    return conversations.synchronizeContext(actor.scope(), control).handle((count, failure) -> failure == null
                            ? "空间绑定已提交并同步会话；请使用 /agent status 或 /cfg agent 查看当前空间。旧空间数据保留。"
                            : "空间绑定已提交，同步观察未完成；请使用 /agent status 或 /cfg agent 核对。旧空间数据保留。");
                });
                return control.guard(work).whenComplete((ignored, failure) -> { if (parent == null) { control.close(); } });
            } catch (RuntimeException failure) { if (parent == null) { control.close(); } return CompletableFuture.failedStage(failure); }
        } catch (RuntimeException failure) { return CompletableFuture.failedStage(failure); }
    }
    /**
     * 列举本人创建的空间，默认分页大小沿用个人配额，不能指定其他 UUID。
     * @param data 原生来源
     * @param offset 分页偏移
     * @return 有界空间列表
     * @throws IllegalStateException 当作用域未启用时
     */
    public CompletionStage<String> namespaces(ITeaNekoMessageData data, int offset) {
        var actor = NativeMessageSources.actor(data);
        if (!scopes.read(actor.scope()).enabled()) {
            throw new IllegalStateException("当前作用域 Agent 未启用。");
        }
        return namespaces.listCreated(actor, offset, Math.min(100, defaults.maxCreated())).thenApply(values -> values.isEmpty() ? "本页没有自建空间。"
                : values.stream().map(value -> value.id().value() + "，创建于 " + value.createdAt()).collect(java.util.stream.Collectors.joining("\n")) + "\n下一页偏移：" + (offset + values.size()));
    }
}
