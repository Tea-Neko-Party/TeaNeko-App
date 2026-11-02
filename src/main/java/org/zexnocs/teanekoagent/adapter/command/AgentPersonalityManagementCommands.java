package org.zexnocs.teanekoagent.adapter.command;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.agent.access.IAgentScopePolicyPort;
import org.zexnocs.teanekoagent.agent.conversation.IAgentConversationControl;
import org.zexnocs.teanekoagent.personality.*;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.actuator.execution.ResourceBudget;
import org.zexnocs.teanekocore.command.api.CommandPermission;
import org.zexnocs.teanekocore.utils.StableDigest;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;

/**
 * 原生 DEBUG 的人格管理边界，权限、操作者和本地基础配置不由模型参数提供。
 * 提交与会话协调分别报告，原消息重放读取持久回执，不重复修改共享补正。
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Service
public final class AgentPersonalityManagementCommands {
    private final IPersonalityStore store;
    private final IPersonalityManagementStore management;
    private final IPersonalityConfiguration configuration;
    private final IAgentScopePolicyPort scopes;
    private final IAgentConversationControl conversations;
    private final ScheduledExecutorService scheduler;
    private final ObjectMapper mapper;

    /**
     * 借用人格、权限和执行设施，构造时不加载数据或启动模型。
     * @param store 人格读取端口
     * @param management 管理事务端口
     * @param configuration 本地可信配置
     * @param scopes 当前启用策略
     * @param conversations 已知会话协调
     * @param scheduler 原截止时间调度器
     * @param mapper 导出编码器
     */
    public AgentPersonalityManagementCommands(IPersonalityStore store, IPersonalityManagementStore management, IPersonalityConfiguration configuration,
            IAgentScopePolicyPort scopes, IAgentConversationControl conversations,
            @Qualifier("taskExecutor") ScheduledExecutorService scheduler, ObjectMapper mapper) {
        this.store = store; this.management = management; this.configuration = configuration; this.scopes = scopes;
        this.conversations = conversations; this.scheduler = scheduler; this.mapper = mapper;
    }

    /**
     * 展示固定版本补正、可追溯导出或备份引用；空 key 使用当前作用域。
     * @param data 原始 DEBUG 来源
     * @param key 目标 key，空值表示当前引用
     * @param offset 页内偏移
     * @param backups 是否查询备份列表
     * @param export 是否导出完整来源 JSON
     * @param parent 借用父控制
     * @return 有界反馈及后继偏移
     */
    public CompletionStage<String> page(ITeaNekoMessageData data, String key, int offset, boolean backups, boolean export, @Nullable ExecutionControl parent) {
        return controlled(data, parent, control -> {
            if (offset < 0 || offset > 64 || key.length() > 256) { throw new IllegalArgumentException("人格查询 key 或偏移无效。"); }
            var actor = NativeCommandBridge.actor(data); var base = configuration.base(configuration.settings(actor.scope()));
            return control.guard(management.inspect(actor.scope(), base, Clock.systemUTC().instant(), () -> guardBase(data, control, base))).thenCompose(selection -> {
                guard(data, control); var selected = key.isBlank() ? selection.state().key() : key;
                if (backups) {
                    return control.guard(management.backups(selected)).thenApply(keys -> {
                        guard(data, control); int end = Math.min(keys.size(), offset + 5);
                        return "源 key=" + selected + "；bindingRevision=" + selection.binding().revision() + "\n"
                                + String.join("\n", keys.subList(Math.min(offset, keys.size()), end)) + "\n下一偏移：" + (end == keys.size() ? "结束" : end);
                    });
                }
                return control.guard(store.state(selected)).thenApply(optional -> {
                    guard(data, control);
                    if (optional.isEmpty()) { return "补正 key 不存在；bindingRevision=" + selection.binding().revision() + "；新建绑定请使用 stateRevision=-1。"; }
                    var state = optional.get(); int end = Math.min(state.corrections().size(), offset + (export ? 2 : 5));
                    var items = state.corrections().subList(Math.min(offset, state.corrections().size()), end);
                    if (export) {
                        var json = mapper.writeValueAsString(Map.of("schemaVersion", 1, "binding", selection.binding(), "key", state.key(), "revision", state.revision(),
                                "managementRevision", state.managementRevision(), "backup", state.backup(), "records", items, "nextOffset", end == state.corrections().size() ? -1 : end));
                        if (json.getBytes(StandardCharsets.UTF_8).length > 131072) { throw new IllegalArgumentException("单页补正原始证据超过聊天导出上限。"); }
                        return json;
                    }
                    var text = new StringBuilder("当前 key=").append(selection.binding().correctionKey()).append("；bindingRevision=").append(selection.binding().revision())
                            .append("\n查询 key=").append(state.key()).append("；stateRevision=").append(state.revision()).append("；backup=").append(state.backup())
                            .append("\n可新增字段：").append(base.mutableFields().stream().map(field -> field.replace(' ', '_')).sorted().toList());
                    for (var item : items) { text.append('\n').append(item.requestId()).append("；字段=").append(item.field()).append("；locked=").append(item.locked())
                            .append("；来源=").append(item.source().userId().orElseThrow()).append("；").append(item.text()); }
                    return text.append("\n下一偏移：").append(end == state.corrections().size() ? "结束" : end).toString();
                });
            });
        });
    }

    /**
     * 使用原生身份构造明确版本的管理动作，保存提交回执后才协调会话。
     * @param data 真实 DEBUG 来源
     * @param action 管理动作
     * @param key 源或目标 key
     * @param bindingRevision 原绑定版本
     * @param stateRevision 原补正版本
     * @param record 可选原补正 UUID
     * @param field 可选新增字段，空格可写为下划线
     * @param text 可选新增或编辑正文
     * @param locked 可选锁定状态
     * @param parent 借用父控制
     * @return 原操作结果和实际协调状态
     */
    public CompletionStage<String> change(ITeaNekoMessageData data, PersonalityManagementCommand.Action action, String key, long bindingRevision,
            long stateRevision, @Nullable String record, @Nullable String field, @Nullable String text, @Nullable Boolean locked, @Nullable ExecutionControl parent) {
        return controlled(data, parent, control -> {
            var actor = NativeCommandBridge.actor(data); var base = configuration.base(configuration.settings(actor.scope()));
            var operation = UUID.nameUUIDFromBytes(StableDigest.sha256(actor.scope().clientId(), actor.scope().scopeId(), data.getMessageId(),
                    "personality-management:" + action).getBytes(StandardCharsets.UTF_8));
            var command = new PersonalityManagementCommand(operation, actor, action, key, bindingRevision, stateRevision,
                    record == null ? null : UUID.fromString(record), field == null ? null : field.replace('_', ' '), text, locked);
            return control.guard(management.apply(command, base, Clock.systemUTC().instant(), () -> guardBase(data, control, base))).thenCompose(result -> {
                var feedback = "人格管理原操作结果：" + result.status() + "；key=" + result.key() + "；stateRevision=" + result.stateRevision()
                        + "；bindingRevision=" + result.bindingRevision() + (result.backupKey() == null ? "" : "；备份=" + result.backupKey());
                if (result.status() != PersonalityManagementResult.Status.APPLIED || action == PersonalityManagementCommand.Action.CLONE
                        || action == PersonalityManagementCommand.Action.BACKUP) { return CompletableFuture.completedStage(feedback); }
                CompletionStage<Integer> synchronization;
                try { synchronization = conversations.synchronizePersonality(actor.scope(), key, action != PersonalityManagementCommand.Action.BIND, control); }
                catch (RuntimeException failure) { synchronization = CompletableFuture.failedStage(failure); }
                return synchronization.handle((count, failure) -> feedback + (failure == null ? "；已协调会话=" + count
                        : "；提交已确认，会话同步未完成，旧快照仍受使用前复核限制") + "。回执不代表当前最新版本，请重新查询。" );
            });
        });
    }

    /**
     * 自有管理调用拥有三十秒时限，借用父控制不负责关闭；异常仍进入原命令反馈链。
     * @param data 原生来源
     * @param parent 可选父控制
     * @param work 受控异步业务
     * @return 完整观察阶段
     */
    private CompletionStage<String> controlled(ITeaNekoMessageData data, @Nullable ExecutionControl parent, Function<ExecutionControl, CompletionStage<String>> work) {
        var clock = Clock.systemUTC(); var control = parent == null
                ? ExecutionControl.withDeadline(new ResourceBudget(Map.of()), clock.instant().plusSeconds(30), clock, scheduler) : parent;
        try {
            guard(data, control);
            return control.guard(work.apply(control)).whenComplete((ignored, failure) -> { if (parent == null) { control.close(); } });
        } catch (RuntimeException failure) { if (parent == null) { control.close(); } return CompletableFuture.failedStage(failure); }
    }

    /**
     * 复核当前真实权限和启用，不能用模型布尔值或其他用户文本替代。
     * @param data 原始消息
     * @param control 当前控制
     * @throws IllegalStateException 当权限或当前准入失效时
     */
    private void guard(ITeaNekoMessageData data, ExecutionControl control) {
        control.checkActive(); var actor = NativeCommandBridge.actor(data);
        if (data.getUserData().getRole().getLevel() > CommandPermission.DEBUG.getLevel() || !scopes.read(actor.scope()).enabled()) {
            throw new IllegalStateException("人格管理仅允许已启用作用域的真实 DEBUG 用户。");
        }
        configuration.settings(actor.scope());
    }

    /**
     * 事务开始和结束均确认实际基础内容未改变，避免按旧白名单修改新人格。
     * @param data 原生来源
     * @param control 当前控制
     * @param base 原始基础人格
     * @throws IllegalStateException 当基础配置已变化时
     */
    private void guardBase(ITeaNekoMessageData data, ExecutionControl control, BasePersonality base) {
        guard(data, control);
        if (!configuration.base(configuration.settings(NativeCommandBridge.actor(data).scope())).fingerprint().equals(base.fingerprint())) {
            throw new IllegalStateException("人格管理期间基础配置已变化。");
        }
    }
}
