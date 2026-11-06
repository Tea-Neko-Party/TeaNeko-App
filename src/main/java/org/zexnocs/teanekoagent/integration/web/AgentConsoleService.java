package org.zexnocs.teanekoagent.integration.web;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.component.memory.admin.IMemoryManagementStore;
import org.zexnocs.teanekoagent.component.memory.admin.MemoryManagementCommand;
import org.zexnocs.teanekoagent.component.memory.admin.MemoryManagementResult;
import org.zexnocs.teanekoagent.component.memory.fact.MemoryFactRecord;
import org.zexnocs.teanekoagent.component.memory.namespace.IMemoryNamespaceStore;
import org.zexnocs.teanekoagent.component.memory.namespace.MemoryNamespaceBindings;
import org.zexnocs.teanekoagent.component.memory.namespace.MemoryNamespaceId;
import org.zexnocs.teanekoagent.component.memory.namespace.MemoryNamespaceReference;
import org.zexnocs.teanekoagent.component.memory.profile.IUserProfileService;
import org.zexnocs.teanekoagent.component.memory.profile.UserProfileRequest;
import org.zexnocs.teanekoagent.component.memory.recall.MemoryRecallLimits;
import org.zexnocs.teanekoagent.component.memory.recall.MemoryRecallRequest;
import org.zexnocs.teanekoagent.component.personality.*;
import org.zexnocs.teanekoagent.runtime.AgentActorContext;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekoagent.runtime.conversation.IAgentConversationControl;
import org.zexnocs.teanekoapp.web.client.ManagementTargets;
import org.zexnocs.teanekocore.plugin.management.ManagementContext;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 复用原领域管理事务和证据投影，不创建平行数据库或模型执行循环。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：显式确认必需结果的非空契约，避免直接解引用可空值。
 * <br>2026/11/06：支持已授权实例空间管理，不借用真实聊天身份。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.8.4-l
 */
@Service
public final class AgentConsoleService {
    private final MemoryNamespaceBindings bindings;
    private final IMemoryNamespaceStore namespaces;
    private final IMemoryManagementStore memories;
    private final IUserProfileService profiles;
    private final IPersonalityStore personalities;
    private final IPersonalityManagementStore personalityManagement;
    private final IPersonalityConfiguration configuration;
    private final IAgentConversationControl conversations;

    /**
     * @param bindings 权威记忆绑定
     * @param namespaces 原空间元数据
     * @param memories 原管理事务
     * @param profiles 原画像证据投影
     * @param personalities 原人格只读端口
     * @param personalityManagement 原补正管理
     * @param configuration 本地可信基础人格
     * @param conversations 原共享会话协调
     */
    public AgentConsoleService(MemoryNamespaceBindings bindings, IMemoryNamespaceStore namespaces,
            IMemoryManagementStore memories, IUserProfileService profiles, IPersonalityStore personalities,
            IPersonalityManagementStore personalityManagement, IPersonalityConfiguration configuration,
            IAgentConversationControl conversations) {
        this.bindings = bindings;
        this.namespaces = namespaces;
        this.memories = memories;
        this.profiles = profiles;
        this.personalities = personalities;
        this.personalityManagement = personalityManagement;
        this.configuration = configuration;
        this.conversations = conversations;
    }

    /**
     * @param context 原网关已授权上下文
     * @param query 明确记忆请求
     * @return 固定代次事实页或原事务回执
     */
    public CompletionStage<Object> memory(ManagementContext context, AgentConsole.MemoryQuery query) {
        context.checkActive();
        var scope = scope(context);
        if (query.action().equals("OPERATION")) {
            var actor = actor(context, Objects.requireNonNull(query.operationId()));
            return object(memories.operation(query.operationId(), actor, context::checkActive)
                    .thenApply(result -> memoryResult(query.operationId(), result, "原事务回执；请重新读取当前代次。")));
        }
        return namespace(scope, query.namespace()).thenCompose(id -> namespaces.find(id).thenCompose(found -> {
            context.checkActive();
            var current = found.orElse(null);
            if (query.action().equals("READ")) {
                if (current == null) {
                    return CompletableFuture.completedStage(new AgentConsole.MemoryView(id.value(), 0, 0,
                            false, List.of(), null));
                }
                if (query.generation() >= 0 && query.generation() != current.generation()) {
                    throw new IllegalStateException("记忆代次已经变化，请从第一页重新读取。");
                }
                return object(memories.page(id, current.generation(), query.after(), 20).thenApply(page -> {
                    context.checkActive();
                    return new AgentConsole.MemoryView(id.value(), current.generation(), current.revision(), true,
                            page.records().stream().map(this::evidence).toList(), page.nextCursor());
                }));
            }
            if (query.namespace().isBlank()) {
                throw new IllegalArgumentException("写入必须固定已读取的空间标识。");
            }
            var operation = Objects.requireNonNull(query.operationId());
            var command = new MemoryManagementCommand(operation, actor(context, operation), id, query.generation(),
                    query.revision(), MemoryManagementCommand.Action.valueOf(query.action()), query.recordId(),
                    query.text(), query.locked());
            return object(memories.apply(command, Instant.now(), context::checkActive).thenCompose(result -> {
                if (!query.action().equals("CLEAR") || result.status() != MemoryManagementResult.Status.APPLIED) {
                    return CompletableFuture.completedStage(memoryResult(operation, result,
                            query.action().equals("CLONE") ? "克隆不自动改变聊天绑定。" : "请重新读取当前事实。"));
                }
                CompletionStage<Integer> synchronization;
                try {
                    context.checkActive();
                    synchronization = conversations.synchronizeNamespace(id.value(), context.control());
                } catch (RuntimeException failure) {
                    synchronization = CompletableFuture.failedStage(failure);
                }
                return synchronization.handle((count, failure) -> memoryResult(operation, result,
                        failure == null ? "逻辑清空已提交；已同步会话：" + count + "。旧代次可通过原清理入口回收。"
                                : "逻辑清空已提交；会话协调未确认，旧引用仍受代次校验，不能据此重试新操作。"));
            }));
        }));
    }

    /**
     * @param context 已授权目标
     * @param query 明确主体和固定代次
     * @return 有界证据画像，不保存一份可覆盖的推断结论
     */
    public CompletionStage<Object> profile(ManagementContext context, AgentConsole.ProfileQuery query) {
        context.checkActive();
        var scope = scope(context);
        return namespace(scope, query.namespace()).thenCompose(id -> namespaces.find(id).thenCompose(found -> {
            context.checkActive();
            var current = found.orElseThrow(() -> new NoSuchElementException("记忆空间尚未登记。"));
            if (query.generation() >= 0 && query.generation() != current.generation()) {
                throw new IllegalStateException("画像空间代次已经变化。");
            }
            var reference = new MemoryNamespaceReference(scope, id, 0, current.generation());
            var request = new UserProfileRequest(reference, query.subjectId(), MemoryRecallRequest.Use.TOOL,
                    new MemoryRecallLimits(30, 16000, 300));
            return object(profiles.read(request).thenApply(view -> {
                context.checkActive();
                return new AgentConsole.ProfileView(query.subjectId(), id.value(), current.generation(),
                        view.isComplete(), view.scanned(), view.matched(), view.evidence().stream().map(item ->
                        new AgentConsole.Evidence(item.id(), item.revision(), item.subjectId(), item.value().text(),
                                item.value().kind().name(), item.locked(), item.createdAt(), item.updatedAt(),
                                item.value().eventTime(), item.source().actor().scope().clientId(),
                                item.source().actor().scope().scopeId())).toList());
            }));
        }));
    }

    /**
     * 2026/11/05：显式确认必需结果的非空契约，避免直接解引用可空值。
     * @param context 原管理权限
     * @param query 原绑定与集合版本
     * @return 基础人格、补正或原管理结果
     */
    public CompletionStage<Object> personality(ManagementContext context, AgentConsole.PersonalityQuery query) {
        context.checkActive();
        var scope = scope(context);
        if (query.action().equals("OPERATION")) {
            var operation = Objects.requireNonNull(query.operationId());
            return object(personalityManagement.operation(operation, actor(context, operation), context::checkActive)
                    .thenApply(result -> personalityResult(operation, result, "原回执，不表示当前绑定仍未变化。")));
        }
        var base = configuration.base(configuration.settings(scope));
        if (query.action().equals("READ")) {
            CompletionStage<Optional<PersonalityBinding>> selectedBinding = personalities.binding(scope);
            return object(selectedBinding.thenCompose(binding -> {
                context.checkActive();
                String key = query.key().isBlank() ? binding.map(PersonalityBinding::correctionKey).orElse("") : query.key();
                CompletionStage<Optional<PersonalityState>> state = key.isEmpty()
                        ? CompletableFuture.completedStage(Optional.empty()) : personalities.state(key);
                return state.thenCompose(value -> personalityManagement.backups(key).thenApply(backups -> {
                    context.checkActive();
                    var current = value.orElse(null);
                    return new AgentConsole.PersonalityView(base.name(), base.rules(), base.mutableFields(), key,
                            binding.map(PersonalityBinding::revision).orElse(0L), current == null ? -1 : current.revision(),
                            binding.map(item -> Objects.equals(item.correctionKey(), key)).orElse(false),
                            current != null && current.backup(), current == null ? List.of() : current.corrections()
                            .stream().map(item -> new AgentConsole.Correction(item.requestId(), item.field(), item.text(),
                                    item.locked(), item.occurredAt(), item.source().origin().name())).toList(), backups);
                }));
            }));
        }
        var operation = Objects.requireNonNull(query.operationId());
        var command = new PersonalityManagementCommand(operation, actor(context, operation),
                PersonalityManagementCommand.Action.valueOf(query.action()), query.key(), query.bindingRevision(),
                query.stateRevision(), query.correctionId(), query.field(), query.text(), query.locked());
        Runnable guard = () -> {
            context.checkActive();
            if (!base.fingerprint().equals(configuration.base(configuration.settings(scope)).fingerprint())) {
                throw new IllegalStateException("基础人格配置已经变化。");
            }
        };
        var changed = personalityManagement.apply(command, base, Instant.now(), guard);
        return object(changed.thenCompose(result -> {
            if (result.status() != PersonalityManagementResult.Status.APPLIED) {
                return CompletableFuture.completedStage(personalityResult(operation, result, "本次未产生新修改。"));
            }
            if (command.action() == PersonalityManagementCommand.Action.CLONE
                    || command.action() == PersonalityManagementCommand.Action.BACKUP) {
                return CompletableFuture.completedStage(personalityResult(operation, result,
                        "副本或备份已经提交，当前聊天绑定未改变。备份引用可在原 key 的最近备份中查看。"));
            }
            CompletionStage<Integer> synchronization;
            try {
                guard.run();
                boolean shared = command.action() != PersonalityManagementCommand.Action.BIND;
                synchronization = conversations.synchronizePersonality(scope, result.key(), shared, context.control());
            } catch (RuntimeException failure) {
                synchronization = CompletableFuture.failedStage(failure);
            }
            return synchronization.handle((count, failure) -> personalityResult(operation, result,
                    failure == null ? "事务已提交；已协调会话：" + count + "。"
                            : "事务已提交；会话协调未确认，请重新读取，不能据此创建新操作重试。"));
        }));
    }

    /**
     * @param scope 已授权聊天
     * @param explicit 管理员明确空间，空串读取权威绑定
     * @return 实际空间，不创建绑定或默认记录
     */
    private CompletionStage<MemoryNamespaceId> namespace(AgentScope scope, String explicit) {
        if (!explicit.isBlank()) {
            return CompletableFuture.completedStage(new MemoryNamespaceId(explicit));
        }
        return bindings.readReference(scope).thenApply(reference ->
                reference.map(MemoryNamespaceReference::namespaceId).orElse(MemoryNamespaceId.SHARED));
    }

    /**
     * @param record 原事实
     * @return 不含原导入 JSON 的证据
     */
    private AgentConsole.Evidence evidence(MemoryFactRecord record) {
        var source = record.source().actor().scope();
        return new AgentConsole.Evidence(record.id(), record.revision(), record.subjectId(), record.value().text(),
                record.value().kind().name(), record.locked(), record.createdAt(), record.updatedAt(),
                record.value().eventTime(), source.clientId(), source.scopeId());
    }

    /**
     * @param context 可信上下文
     * @return 实际聊天，不从请求 DTO 读取 scope
     */
    static AgentScope scope(ManagementContext context) {
        // 2026/11/06：实例管理有自己的来源域，不伪造任何客户端聊天或登记绑定。
        if (global(context)) {
            return new AgentScope("management", "instance");
        }
        var target = ManagementTargets.decode(context.scopeRef());
        return new AgentScope(target.client(), target.scope());
    }

    /**
     * 2026/11/06：仅宿主实例资源入口创建 global 上下文，浏览器不能填写该值。
     * @param context 可信管理上下文
     * @return 是否为独立实例管理
     */
    static boolean global(ManagementContext context) {
        return context.scopeRef().equals("global");
    }

    /**
     * @param context 原主体
     * @param operation 原操作，重放时保持来源不变
     * @return 独立管理来源 UUID，不注册平台用户或继承聊天用户身份
     */
    static AgentActorContext actor(ManagementContext context, UUID operation) {
        var subject = UUID.nameUUIDFromBytes(("management:" + context.subject()).getBytes(StandardCharsets.UTF_8));
        return new AgentActorContext(scope(context), AgentActorContext.Origin.MANAGEMENT, Optional.of(subject), "",
                operation.toString(), context.scopeRef());
    }

    /**
     * @param operation 原操作
     * @param result 原持久化结果
     * @param note 后续协调说明
     * @return 明确事务回执
     */
    private AgentConsole.ChangeView memoryResult(UUID operation, MemoryManagementResult result, String note) {
        var namespace = result.namespace();
        return new AgentConsole.ChangeView(operation, result.status().name(), namespace == null ? "" : namespace.id().value(),
                namespace == null ? -1 : namespace.generation(), result.record() != null ? result.record().revision()
                : namespace == null ? -1 : namespace.revision(), note);
    }

    /**
     * @param operation 原操作
     * @param result 原持久化结果
     * @param note 后续协调说明
     * @return 明确事务回执
     */
    static AgentConsole.ChangeView personalityResult(UUID operation, PersonalityManagementResult result, String note) {
        return new AgentConsole.ChangeView(operation, result.status().name(), result.key(), -1, result.stateRevision(), note);
    }

    /**
     * @param stage 明确 DTO 阶段
     * @param <T> 原视图类型
     * @return 供原资源协议返回的对象阶段，不进行未经检查的转换
     */
    private static <T> CompletionStage<Object> object(CompletionStage<T> stage) {
        return stage.thenApply(value -> value);
    }
}
