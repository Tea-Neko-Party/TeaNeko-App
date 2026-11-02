package org.zexnocs.teanekoagent.adapter.memory;

import org.jspecify.annotations.Nullable;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.database.easydata.TeaNekoAgentEasyData;
import org.zexnocs.teanekoagent.memory.admin.IMemoryManagementStore;
import org.zexnocs.teanekoagent.memory.admin.MemoryManagementCommand;
import org.zexnocs.teanekoagent.memory.admin.MemoryManagementResult;
import org.zexnocs.teanekoagent.memory.fact.*;
import org.zexnocs.teanekoagent.memory.namespace.IMemoryNamespaceDefaults;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespace;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceId;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceMutation;
import org.zexnocs.teanekocore.database.easydata.core.EasyDataLock;
import org.zexnocs.teanekocore.utils.StableDigest;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletionStage;

/**
 * 在原数据库事务中完成 DEBUG 管理，空间行锁协调 clear、追加、编辑和一致快照克隆。
 * 回执与数据同事务保存；物理回收仅处理明确的旧代次事实域，不触碰全局防重记录。
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Service
public final class EasyDataMemoryManagementStore implements IMemoryManagementStore {
    private final EasyDataMemoryNamespaceStore namespaces;
    private final EasyDataMemoryFactStore facts;
    private final TeaNekoAgentEasyData data;
    private final IMemoryNamespaceDefaults defaults;
    private final ObjectMapper mapper;
    private final Environment environment;

    /**
     * 保存原存储适配器和有界快照策略，不访问数据库。
     * @param namespaces 空间事务适配器
     * @param facts 事实结构及内容索引
     * @param data Agent 专用数据入口
     * @param defaults 创建配额
     * @param mapper 持久化 JSON
     * @param environment 懒读的本地快照限制，非法配置只使管理操作失败
     */
    public EasyDataMemoryManagementStore(EasyDataMemoryNamespaceStore namespaces, EasyDataMemoryFactStore facts,
            TeaNekoAgentEasyData data, IMemoryNamespaceDefaults defaults, ObjectMapper mapper,
            Environment environment) {
        this.namespaces = namespaces; this.facts = facts; this.data = data.inNamespace("agent.fact.v1");
        this.defaults = defaults; this.mapper = mapper; this.environment = environment;
    }

    /**
     * 先固定空间和原请求回执，再校验版本，保证未知重复不会再次推进 clear 或创建克隆。
     * @param command 已授权的固定管理操作
     * @param now 原始提交时间
     * @param guard 当前授权及取消检查
     * @return 原事务结果
     */
    @Override public CompletionStage<MemoryManagementResult> apply(MemoryManagementCommand command, Instant now, Runnable guard) {
        Objects.requireNonNull(command, "管理命令不能为空。"); Objects.requireNonNull(now, "管理时刻不能为空。");
        return namespaces.submit("管理记忆空间事实", () -> {
            guard.run();
            var row = namespaces.lockNamespace(command.namespace(), EasyDataLock.WRITE);
            if (row == null) { return result(MemoryManagementResult.Status.NOT_FOUND, null); }
            var namespace = namespaces.namespace(row, command.namespace());
            var receipt = namespaces.dto("management-operation").find(command.operationId().toString(), EasyDataLock.READ);
            if (receipt != null) {
                var original = namespaces.read(receipt, OperationData.class);
                return original.command().equals(command) ? original.result() : result(MemoryManagementResult.Status.CONFLICT, namespace);
            }
            if (namespace.generation() != command.generation()) { return result(MemoryManagementResult.Status.CONFLICT, namespace); }
            MemoryManagementResult outcome;
            if (command.action() == MemoryManagementCommand.Action.EDIT) { outcome = edit(command, namespace, now); }
            else if (namespace.revision() != command.revision()) { outcome = result(MemoryManagementResult.Status.CONFLICT, namespace); }
            else if (command.action() == MemoryManagementCommand.Action.CLEAR) {
                var changed = namespace.nextGeneration(command.operationId()); namespaces.saveNamespaceInTransaction(changed);
                outcome = result(MemoryManagementResult.Status.APPLIED, changed);
            } else { outcome = cloneSnapshot(command, namespace, now); }
            if (outcome.status() == MemoryManagementResult.Status.APPLIED) {
                guard.run();
                namespaces.write(null, "management-operation", command.operationId().toString(), new OperationData(1, command, outcome));
            }
            return outcome;
        });
    }

    /**
     * 在版本检查与完整有界快照内编辑正文或锁定，保留事实主体、原来源和事件语义。
     * @param command 编辑请求
     * @param namespace 锁定的空间
     * @param now 本次提交时刻
     * @return 编辑结果或未执行原因
     */
    private MemoryManagementResult edit(MemoryManagementCommand command, MemoryNamespace namespace, Instant now) {
        var records = snapshot(namespace);
        if (records == null) { return result(MemoryManagementResult.Status.TOO_LARGE, namespace); }
        var before = records.stream().filter(record -> record.id().equals(command.recordId())).findFirst().orElse(null);
        if (before == null) { return result(MemoryManagementResult.Status.NOT_FOUND, namespace); }
        if (before.revision() != command.revision()) { return result(MemoryManagementResult.Status.CONFLICT, namespace); }
        var value = before.value();
        if (command.text() != null) { value = new MemoryFactValue(value.kind(), command.text(), value.tags(), value.confidence(), value.importance(), value.eventTime(), value.expiresAt()); }
        var changed = new MemoryFactRecord(before.id(), namespace.id(), namespace.generation(), Math.incrementExact(before.revision()),
                before.subjectId(), value, before.source(), before.createdAt(), now.isBefore(before.updatedAt()) ? before.updatedAt() : now,
                command.locked() == null ? before.locked() : command.locked(), before.provenance());
        return facts.replaceInTransaction(before, changed, records)
                ? new MemoryManagementResult(MemoryManagementResult.Status.APPLIED, namespace, 0, changed)
                : result(MemoryManagementResult.Status.CONFLICT, namespace);
    }

    /**
     * 将事务开始时锁定的源事实复制到独立新空间，画像由目标事实重建，不复制绑定或人格。
     * @param command 原克隆操作
     * @param source 锁定源空间
     * @param now 新空间创建时间
     * @return 创建与全部记录同事务的结果
     */
    private MemoryManagementResult cloneSnapshot(MemoryManagementCommand command, MemoryNamespace source, Instant now) {
        var records = snapshot(source);
        if (records == null) { return result(MemoryManagementResult.Status.TOO_LARGE, source); }
        var creation = new MemoryNamespace.Creation(command.operationId(), command.actor());
        var target = new MemoryNamespace(MemoryNamespaceId.created(command.operationId()), 0, 0, now, creation, null);
        var created = namespaces.createInTransaction(target, defaults.maxCreated());
        if (created.status() == MemoryNamespaceMutation.Status.QUOTA_EXCEEDED) { return result(MemoryManagementResult.Status.QUOTA_EXCEEDED, null); }
        if (created.status() != MemoryNamespaceMutation.Status.CREATED) { return result(MemoryManagementResult.Status.CONFLICT, source); }
        for (var record : records) {
            var raw = encode(record);
            var provenance = new MemoryFactProvenance("teaneko-clone-v1", source.id().value(), "g:" + source.generation(), record.id().toString(),
                    StableDigest.sha256(raw), raw, Set.of("复制事实快照，保留主体、事件时间、原来源及锁定；不复制人格、会话或运行任务。"));
            var id = UUID.nameUUIDFromBytes(StableDigest.sha256(command.operationId().toString(), record.id().toString()).getBytes(StandardCharsets.UTF_8));
            facts.insertSnapshotInTransaction(new MemoryFactRecord(id, target.id(), 0, 0, record.subjectId(), record.value(),
                    record.source(), record.createdAt(), record.updatedAt(), record.locked(), provenance));
        }
        return new MemoryManagementResult(MemoryManagementResult.Status.APPLIED, target, records.size(), null);
    }

    /**
     * 在调用者已持有空间锁时按固定页读取，超出记录或字符预算则拒绝整批，不返回截断克隆。
     * @param namespace 当前元数据
     * @return 完整记录，超过预算时为空
     */
    private @Nullable List<MemoryFactRecord> snapshot(MemoryNamespace namespace) {
        final int maxRecords;
        final int maxBytes;
        try {
            maxRecords = environment.getProperty("tea-neko.agent.memory.management.max-records", Integer.class, 1000);
            maxBytes = environment.getProperty("tea-neko.agent.memory.management.max-bytes", Integer.class, 1048576);
            if (maxRecords < 1 || maxRecords > 10000 || maxBytes < 1024 || maxBytes > 16777216) { throw new IllegalArgumentException(); }
        } catch (RuntimeException invalid) { throw new IllegalStateException("记忆管理快照限制无效，请检查本地配置。"); }
        var records = new ArrayList<MemoryFactRecord>(); UUID after = null; long bytes = 0;
        do {
            var page = facts.listLockedInTransaction(namespace.id(), namespace.generation(), after, 100);
            for (var record : page.records()) {
                bytes += encode(record).getBytes(StandardCharsets.UTF_8).length;
                if (records.size() == maxRecords || bytes > maxBytes) { return null; }
                records.add(record);
            }
            after = page.nextCursor();
        } while (after != null);
        return List.copyOf(records);
    }

    /**
     * 共享快照锁保护单页导出；客户端必须带回固定代次，不能在 clear 后续读旧分页。
     * @param namespace 空间
     * @param generation 固定代次
     * @param after 前页游标
     * @param limit 一至一百的页大小
     * @return 当前代次的原始证据页
     * @throws IllegalArgumentException 当分页或代次无效时
     */
    @Override public CompletionStage<MemoryFactPage> page(MemoryNamespaceId namespace, long generation, @Nullable UUID after, int limit) {
        if (generation < 0 || limit < 1 || limit > 100) { throw new IllegalArgumentException("记忆管理分页参数无效。"); }
        return namespaces.submit("导出记忆证据页", () -> {
            var row = namespaces.lockNamespace(namespace, EasyDataLock.READ);
            if (row == null || namespaces.namespace(row, namespace).generation() != generation) { throw new MemoryFactGenerationException(row == null); }
            return facts.listLockedInTransaction(namespace, generation, after, limit);
        });
    }

    /**
     * 仅删除明确旧代次的三个事实目标，每个事务有固定行数上限，不触碰人格、历史和命令账本。
     * @param namespace 空间
     * @param obsoleteGeneration 已失效代次
     * @param limit 单批删除上限
     * @param guard 当前授权及取消检查
     * @return 本批删除行数
     * @throws IllegalArgumentException 当代次或批量限制无效时
     */
    @Override public CompletionStage<Integer> purge(MemoryNamespaceId namespace, long obsoleteGeneration, int limit, Runnable guard) {
        if (obsoleteGeneration < 0 || limit < 1 || limit > 1000) { throw new IllegalArgumentException("旧代次回收参数无效。"); }
        return namespaces.submit("分批回收旧代次事实", () -> {
            guard.run();
            var metadata = namespaces.lockNamespace(namespace, EasyDataLock.WRITE);
            if (metadata == null || obsoleteGeneration >= namespaces.namespace(metadata, namespace).generation()) {
                throw new IllegalArgumentException("只能回收已登记空间的旧代次。");
            }
            int deleted = 0;
            for (var kind : List.of("record", "duplicate", "operation")) {
                if (deleted == limit) { break; }
                var target = kind + ":" + namespace.value() + ":g:" + obsoleteGeneration;
                var dto = data.get(target).atomic();
                var rows = dto.scan("", null, limit - deleted, EasyDataLock.WRITE);
                for (var row : rows) { dto.delete(row); deleted++; }
            }
            guard.run(); return deleted;
        });
    }

    /**
     * 编码明确存储结构，异常不携带原文。
     * @param value 数据对象
     * @return JSON 原文
     * @throws IllegalStateException 当编码失败时
     */
    private String encode(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (RuntimeException failure) { throw new IllegalStateException("记忆管理数据无法编码。"); }
    }

    /**
     * 构造无编辑记录的状态回执。
     * @param status 结果状态
     * @param namespace 观察元数据
     * @return 固定业务结果
     */
    private MemoryManagementResult result(MemoryManagementResult.Status status, @Nullable MemoryNamespace namespace) {
        return new MemoryManagementResult(status, namespace, 0, null);
    }

    /**
     * 防重结构保留原始预期版本和完整参数，跨命令重用同 ID 明确冲突。
     * @param schemaVersion 存储版本
     * @param command 原请求
     * @param result 原提交结果
     * @author zExNocs
     * @date 2026/11/01
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private record OperationData(int schemaVersion, MemoryManagementCommand command, MemoryManagementResult result) {
        /**
         * 拒绝未知版本、缺失命令或非成功回执，不让损坏防重记录触发重写。
         * @param schemaVersion 存储版本
         * @param command 原命令
         * @param result 已提交结果
         * @throws IllegalArgumentException 当结构不完整时
         */
        private OperationData {
            if (schemaVersion != 1 || command == null || result == null || result.status() != MemoryManagementResult.Status.APPLIED) {
                throw new IllegalArgumentException("记忆管理回执结构无效。");
            }
            var namespace = Objects.requireNonNull(result.namespace(), "管理成功回执缺少空间。");
            var creation = namespace.creation(); var edited = result.record();
            var expectedId = command.action() == MemoryManagementCommand.Action.CLONE ? MemoryNamespaceId.created(command.operationId()) : command.namespace();
            if (!namespace.id().equals(expectedId)
                    || command.action() == MemoryManagementCommand.Action.CLEAR && (command.generation() == Long.MAX_VALUE || namespace.generation() != command.generation() + 1
                        || !command.operationId().equals(namespace.lastGenerationRequest()))
                    || command.action() == MemoryManagementCommand.Action.CLONE && (namespace.generation() != 0 || creation == null
                        || !creation.requestId().equals(command.operationId()) || !creation.actor().equals(command.actor()))
                    || command.action() == MemoryManagementCommand.Action.EDIT && (namespace.generation() != command.generation() || edited == null
                        || !edited.id().equals(command.recordId()) || !edited.namespaceId().equals(command.namespace())
                        || edited.generation() != command.generation())) {
                throw new IllegalArgumentException("记忆管理回执与原操作定位不一致。");
            }
        }
    }
}
