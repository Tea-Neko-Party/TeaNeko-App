package org.zexnocs.teanekoplugin.agent.cognition.integration.command;

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

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
import org.zexnocs.teanekoplugin.agent.cognition.memory.admin.IMemoryManagementStore;
import org.zexnocs.teanekoplugin.agent.cognition.memory.admin.MemoryManagementCommand;
import org.zexnocs.teanekoplugin.agent.cognition.memory.admin.MemoryManagementResult;
import org.zexnocs.teanekoplugin.agent.cognition.memory.namespace.IMemoryNamespaceStore;
import org.zexnocs.teanekoplugin.agent.cognition.memory.namespace.MemoryNamespaceId;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

/**
 * 真实 DEBUG 来源的记忆管理边界，模型只提供业务参数，不能替换操作者或提升权限。
 * 逻辑清空提交、会话同步与后续物理回收分别报告，不伪装成分布式原子操作。
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
public final class AgentMemoryManagementCommands {
    private final IMemoryManagementStore store;
    private final IMemoryNamespaceStore namespaces;
    private final IAgentScopePolicyPort scopes;
    private final IAgentConversationControl conversations;
    private final ScheduledExecutorService scheduler;
    private final ObjectMapper mapper;

    /**
     * 保存管理端口与原执行设施，不自动加载事实或创建空间。
     * @param store 管理事务端口
     * @param namespaces 空间元数据
     * @param scopes 当前启用状态
     * @param conversations 共享空间会话同步
     * @param scheduler 原截止时间调度器
     * @param mapper 有界导出 JSON 编码器
     */
    public AgentMemoryManagementCommands(IMemoryManagementStore store, IMemoryNamespaceStore namespaces, IAgentScopePolicyPort scopes,
            IAgentConversationControl conversations, @Qualifier("taskExecutor") ScheduledExecutorService scheduler, ObjectMapper mapper) {
        this.store = store; this.namespaces = namespaces; this.scopes = scopes;
        this.conversations = conversations; this.scheduler = scheduler; this.mapper = mapper;
    }

    /**
     * 展示元数据及分页事实，导出包含完整证据，后继页必须显式固定代次。
     * @param data 真实 DEBUG 来源
     * @param namespace 目标空间
     * @param generation 固定代次，-1 只允许首次查询当前代次
     * @param after 前页 UUID，空值为首屏
     * @param export 是否生成可拼接保存的 JSON
     * @param parent 借用父控制
     * @return 有界页面或明确不存在反馈
     */
    public CompletionStage<String> page(ITeaNekoMessageData data, String namespace, long generation, String after, boolean export,
            @Nullable ExecutionControl parent) {
        return controlled(data, parent, control -> {
            if (generation < -1 || generation == -1 && !after.isBlank()) { throw new IllegalArgumentException("后续页面必须带回原代次。"); }
            var id = new MemoryNamespaceId(namespace); var cursor = after.isBlank() ? null : UUID.fromString(after);
            return control.guard(namespaces.find(id)).thenCompose(optional -> {
                guard(data, control);
                if (optional.isEmpty()) { return CompletableFuture.completedStage("该记忆空间不存在。"); }
                var metadata = optional.get(); long fixed = generation == -1 ? metadata.generation() : generation;
                return control.guard(store.page(id, fixed, cursor, export ? 1 : 5)).thenApply(page -> {
                    guard(data, control);
                    if (export) {
                        var json = mapper.writeValueAsString(Map.of("schemaVersion", 1, "namespace", metadata, "generation", fixed, "page", page));
                        if (json.getBytes(StandardCharsets.UTF_8).length > 131072) { throw new IllegalArgumentException("单页原始证据超过聊天导出上限。"); }
                        return json;
                    }
                    var text = new StringBuilder(id.value()).append("；generation=").append(fixed).append("；revision=").append(metadata.revision());
                    for (var record : page.records()) {
                        text.append('\n').append(record.id()).append("；revision=").append(record.revision()).append("；locked=").append(record.locked())
                                .append("；subject=").append(record.subjectId()).append("；").append(record.value().text());
                    }
                    return text.append("\n下一页游标：").append(page.nextCursor() == null ? "结束" : page.nextCursor()).toString();
                });
            });
        });
    }

    /**
     * 修改正文或锁定状态，固定原事实版本，不允许迟到编辑覆盖较新管理结果。
     * @param data 原生 DEBUG 来源
     * @param namespace 空间
     * @param generation 原代次
     * @param recordId 记录 UUID
     * @param revision 事实版本
     * @param text 可选替换正文
     * @param locked 可选锁定状态
     * @param parent 父控制
     * @return 原事务状态
     */
    public CompletionStage<String> edit(ITeaNekoMessageData data, String namespace, long generation, String recordId, long revision,
            @Nullable String text, @Nullable Boolean locked, @Nullable ExecutionControl parent) {
        return controlled(data, parent, control -> {
            var command = new MemoryManagementCommand(operation(data, "edit"), NativeMessageSources.actor(data), new MemoryNamespaceId(namespace),
                    generation, revision, MemoryManagementCommand.Action.EDIT, UUID.fromString(recordId), text, locked);
            return control.guard(store.apply(command, Clock.systemUTC().instant(), () -> guard(data, control))).thenApply(result -> {
                var record = result.record();
                return "记忆编辑结果：" + result.status() + (record == null ? "" : "；记录=" + record.id() + "；revision=" + record.revision() + "；locked=" + record.locked());
            });
        });
    }

    /**
     * 清空推进 generation，克隆生成独立空间；原请求重放只返回原提交，不重建副作用。
     * @param data 原 DEBUG 来源
     * @param namespace 源或目标空间
     * @param generation 原数据代次
     * @param revision 原空间版本
     * @param clone 是否克隆，否则清空
     * @param parent 借用父控制
     * @return 包含后续同步及回收实际状态的反馈
     */
    public CompletionStage<String> change(ITeaNekoMessageData data, String namespace, long generation, long revision, boolean clone,
            @Nullable ExecutionControl parent) {
        return controlled(data, parent, control -> {
            var command = new MemoryManagementCommand(operation(data, clone ? "clone" : "clear"), NativeMessageSources.actor(data), new MemoryNamespaceId(namespace),
                    generation, revision, clone ? MemoryManagementCommand.Action.CLONE : MemoryManagementCommand.Action.CLEAR, null, null, null);
            return control.guard(store.apply(command, Clock.systemUTC().instant(), () -> guard(data, control))).thenCompose(result -> {
                if (result.status() != MemoryManagementResult.Status.APPLIED) { return CompletableFuture.completedStage("记忆管理未提交：" + result.status()); }
                var target = java.util.Objects.requireNonNull(result.namespace());
                if (clone) { return CompletableFuture.completedStage("克隆已提交：" + target.id().value() + "；事实=" + result.records() + "。原空间、绑定、人格及历史未改变。"); }
                CompletionStage<Integer> synchronizedState;
                try {
                    synchronizedState = conversations.synchronizePartition(target.id().value(), control);
                }
                catch (RuntimeException failure) { synchronizedState = CompletableFuture.failedStage(failure); }
                return synchronizedState.handle((count, failure) -> failure == null ? "已同步共享空间会话" : "会话同步未能确认，旧引用仍受提交校验限制")
                        .thenCompose(sync -> store.purge(target.id(), command.generation(), 100, () -> guard(data, control))
                                .handle((deleted, failure) -> "逻辑清空已提交；原操作目标 generation=" + target.generation() + "；" + sync
                                        + (failure == null ? "；本批回收=" + deleted : "；后续回收未完成")
                                        + "。可继续 /agent purgememory 回收旧代次；原操作回执不代表当前最新代次。"));
            });
        });
    }

    /**
     * 继续回收明确旧代次，单次至多一百行，可重复执行直到返回零。
     * @param data 原 DEBUG 来源
     * @param namespace 空间
     * @param generation 已失效代次
     * @param parent 借用父控制
     * @return 已删除行数
     */
    public CompletionStage<String> purge(ITeaNekoMessageData data, String namespace, long generation, @Nullable ExecutionControl parent) {
        return controlled(data, parent, control -> control.guard(store.purge(new MemoryNamespaceId(namespace), generation, 100,
                () -> guard(data, control))).thenApply(count -> "本批旧代次已回收 " + count + " 行；返回零表示该代次事实域已处理完毕。"));
    }

    /**
     * 管理观察拥有自己的截止时间，工具调用借用父控制，数据库任务内重复复核当前授权。
     * @param data 原始来源
     * @param parent 可选父控制
     * @param work 受控异步操作
     * @return 不阻塞线程的管理结果
     */
    private CompletionStage<String> controlled(ITeaNekoMessageData data, @Nullable ExecutionControl parent,
            Function<ExecutionControl, CompletionStage<String>> work) {
        var control = parent == null ? ExecutionControl.withDeadline(new ResourceBudget(Map.of()),
                Clock.systemUTC().instant().plusSeconds(30), Clock.systemUTC(), scheduler) : parent;
        try {
            guard(data, control);
            return control.guard(work.apply(control)).whenComplete((ignored, failure) -> { if (parent == null) { control.close(); } });
        } catch (RuntimeException failure) { if (parent == null) { control.close(); } return CompletableFuture.failedStage(failure); }
    }

    /**
     * 原命令权限之外再次复核真实 DEBUG、当前启用和取消，系统/其他用户文本不能替代来源。
     * @param data 实际消息
     * @param control 当前管理控制
     * @throws IllegalStateException 当权限、启用或执行状态失效时
     */
    private void guard(ITeaNekoMessageData data, ExecutionControl control) {
        control.checkActive();
        var actor = NativeMessageSources.actor(data);
        if (data.getUserData().getRole().getLevel() > CommandPermission.DEBUG.getLevel() || !scopes.read(actor.scope()).enabled()) {
            throw new IllegalStateException("记忆管理仅允许已启用作用域的真实 DEBUG 用户。");
        }
    }

    /**
     * 固定原生来源和管理动作，重放改变参数时由持久化原命令仲裁冲突。
     * @param data 实际消息
     * @param action 固定动作
     * @return 原消息的稳定管理 ID
     */
    private UUID operation(ITeaNekoMessageData data, String action) {
        return UUID.nameUUIDFromBytes(StableDigest.sha256(data.getClient().getClientId(), data.getScopeId(), data.getMessageId(),
                "memory-management:" + action).getBytes(StandardCharsets.UTF_8));
    }
}
