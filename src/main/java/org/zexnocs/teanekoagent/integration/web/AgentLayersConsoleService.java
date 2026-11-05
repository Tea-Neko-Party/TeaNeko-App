package org.zexnocs.teanekoagent.integration.web;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.component.memory.layered.ILayeredMemoryStore;
import org.zexnocs.teanekoagent.component.memory.layered.ILongTermMemoryStore;
import org.zexnocs.teanekoagent.component.memory.layered.LongMemories;
import org.zexnocs.teanekoagent.component.memory.layered.LongMemoryJobs;
import org.zexnocs.teanekoagent.component.memory.namespace.IMemoryNamespaceStore;
import org.zexnocs.teanekoagent.component.memory.namespace.MemoryNamespaceBindings;
import org.zexnocs.teanekoagent.component.memory.namespace.MemoryNamespaceId;
import org.zexnocs.teanekoagent.component.memory.namespace.MemoryNamespaceReference;
import org.zexnocs.teanekoagent.runtime.AgentActorContext;
import org.zexnocs.teanekocore.plugin.management.ManagementContext;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 从原持久化端口查看短中长期记忆，管理维护保留原索引与墓碑，不借记忆主体冒充用户。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：接入持久整理状态与原管理权限下的暂停恢复，不调用模型。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.8.3-l
 */
@Service
public final class AgentLayersConsoleService {
    private final ILayeredMemoryStore layers;
    private final ILongTermMemoryStore longTerm;
    private final MemoryNamespaceBindings bindings;
    private final IMemoryNamespaceStore namespaces;
    private final Environment environment;

    /**
     * @param layers 原短期与中期存储
     * @param longTerm 原长期整理存储
     * @param bindings 权威引用
     * @param namespaces 原元数据
     * @param environment 原中期保留策略
     */
    public AgentLayersConsoleService(ILayeredMemoryStore layers, ILongTermMemoryStore longTerm,
            MemoryNamespaceBindings bindings, IMemoryNamespaceStore namespaces, Environment environment) {
        this.layers = layers;
        this.longTerm = longTerm;
        this.bindings = bindings;
        this.namespaces = namespaces;
        this.environment = environment;
    }

    /**
     * @param context 原网关能力
     * @param query 固定目标及动作
     * @return 有界三层视图或真实长期管理回执
     */
    public CompletionStage<Object> execute(ManagementContext context, AgentLayersConsole.Query query) {
        context.checkActive();
        var scope = AgentConsoleService.scope(context);
        // 2026/11/05：只查询原操作结果，迟到轮询不能重复解除暂停。
        if (query.action().equals("REPLAY_OPERATION")) {
            var reference = new MemoryNamespaceReference(scope, new MemoryNamespaceId(query.namespace()), 0, query.generation());
            return longTerm.consolidationOperation(reference, actor(context, query), Objects.requireNonNull(query.operationId()),
                    context::checkActive).thenApply(state -> replayed(query, state));
        }
        if (query.action().equals("OPERATION")) {
            var reference = new MemoryNamespaceReference(scope, new MemoryNamespaceId(query.namespace()), 0, query.generation());
            return longTerm.managementOperation(reference, actor(context, query), query.operationId(), context::checkActive)
                    .thenApply(item -> changed(query, item));
        }
        return bindings.readReference(scope).thenCompose(bound -> {
            var id = query.namespace().isBlank() ? bound.map(MemoryNamespaceReference::namespaceId)
                    .orElse(MemoryNamespaceId.SHARED) : new MemoryNamespaceId(query.namespace());
            return namespaces.find(id).thenCompose(found -> {
                context.checkActive();
                if (found.isEmpty() && query.action().equals("READ")) {
                    return CompletableFuture.completedStage(new AgentLayersConsole.View(id.value(), 0, List.of(),
                            List.of(), 0, new LongMemories.Result(List.of(), 0, false), null));
                }
                var namespace = found.orElseThrow(() -> new NoSuchElementException("记忆空间尚未登记。"));
                if (query.generation() >= 0 && query.generation() != namespace.generation()) {
                    throw new IllegalStateException("分层记忆代次已经变化，请重新读取。");
                }
                long bindingRevision = bound.filter(value -> Objects.equals(value.namespaceId(), id))
                        .map(MemoryNamespaceReference::bindingRevision).orElse(0L);
                var reference = new MemoryNamespaceReference(scope, id, bindingRevision, namespace.generation());
                // 2026/11/05：恢复固定当前绑定，不能借跨空间查看恢复其他分区。
                if (query.action().equals("REPLAY")) {
                    if (bound.isEmpty() || !bound.get().equals(reference)) {
                        throw new IllegalStateException("整理恢复仅允许当前聊天正在使用的记忆空间。");
                    }
                    return longTerm.resumeConsolidation(reference,
                            actor(context, query), query.revision(), Objects.requireNonNull(query.operationId()),
                            Instant.now(), context::checkActive).thenApply(state -> replayed(query, state));
                }
                if (!query.action().equals("READ")) {
                    return longTerm.manage(reference, actor(context, query), Objects.requireNonNull(query.id()),
                            query.revision(), query.text(), Objects.requireNonNull(query.operationId()), Instant.now(),
                            context::checkActive).thenApply(item -> changed(query, item));
                }
                long days = Objects.requireNonNull(environment.getProperty(
                        "tea-neko.agent.memory.layered.retention-days", Long.class, 90L));
                if (days < 1 || days > 365) {
                    throw new IllegalStateException("中期记忆保留天数无效。");
                }
                return layers.read(reference, Instant.now(), Duration.ofDays(days)).thenCombine(
                        longTerm.query(reference, query.subjectId(), query.topic(), 20), (view, learned) -> {
                            context.checkActive();
                            var recent = view.recent().stream().map(value -> new AgentLayersConsole.Recent(
                                    value.sequence(), value.message().role().name(), clipped(value.message().text()),
                                    value.message().occurredAt(), value.message().estimatedTime(), value.message().truncated()
                                    || value.message().text().codePointCount(0, value.message().text().length()) > 4096)).toList();
                            var episodes = view.segments().stream().map(value -> new AgentLayersConsole.Episode(value.id(),
                                    value.digest().brief(), value.digest().details(), value.occurredFrom(), value.occurredThrough())).toList();
                            return new AgentLayersConsole.View(id.value(), namespace.generation(), recent, episodes,
                                    view.compressedThrough(), learned, null);
                        }).thenCombine(longTerm.consolidationState(reference, Instant.now(), context::checkActive), (view, state) -> {
                            context.checkActive();
                            return new AgentLayersConsole.View(view.namespace(), view.generation(), view.recent(), view.episodes(),
                                    view.compressedThrough(), view.longTerm(), state);
                        });
            });
        });
    }

    /**
     * 2026/11/05：报告恢复提交，不声称模型已经重跑或水位已经推进。
     * @param query 原操作
     * @param state 原提交状态
     * @return 与其他管理操作一致的回执
     */
    private AgentConsole.ChangeView replayed(AgentLayersConsole.Query query, LongMemoryJobs.Status state) {
        return new AgentConsole.ChangeView(Objects.requireNonNull(query.operationId()), "APPLIED", query.namespace(),
                query.generation(), state.revision(), "已解除本分区整理暂停；水位未推进，后续按当前开关、权限和模型预算重新处理。");
    }

    /**
     * @param context 原主体
     * @param query 原操作编号
     * @return 独立管理来源，重试时固定
     */
    private AgentActorContext actor(ManagementContext context, AgentLayersConsole.Query query) {
        var user = UUID.nameUUIDFromBytes(("management:" + context.subject()).getBytes(StandardCharsets.UTF_8));
        return new AgentActorContext(AgentConsoleService.scope(context), AgentActorContext.Origin.MANAGEMENT,
                Optional.of(user), "", Objects.requireNonNull(query.operationId()).toString(), context.scopeRef());
    }

    /**
     * @param query 原操作
     * @param item 原提交条目
     * @return 真实持久化结果，不表示原始聊天已删除
     */
    private AgentConsole.ChangeView changed(AgentLayersConsole.Query query, LongMemories.Item item) {
        return new AgentConsole.ChangeView(Objects.requireNonNull(query.operationId()), "APPLIED", item.id().toString(),
                query.generation(), item.revision(), "长期记忆事务已提交；保留原证据抑制和遗忘墓碑，不删除原始聊天。");
    }

    /**
     * @param value 原纯文本
     * @return 最多四千零九十六码点，不拆开代理对
     */
    private static String clipped(String value) {
        return value.codePointCount(0, value.length()) > 4096
                ? value.substring(0, value.offsetByCodePoints(0, 4096)) : value;
    }
}
