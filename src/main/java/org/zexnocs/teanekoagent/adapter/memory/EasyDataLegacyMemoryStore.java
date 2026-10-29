package org.zexnocs.teanekoagent.adapter.memory;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.memory.fact.MemoryFactRecord;
import org.zexnocs.teanekoagent.memory.fact.legacy.*;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletionStage;

import static org.zexnocs.teanekoagent.memory.fact.legacy.LegacyMemoryImportResult.Status;

/**
 * 可信管理方显式预览、导入旧行的适配器；构造和普通事实读取不触发迁移。
 * 目标代次、源行版本、全局旧记录映射和请求回执在同一个 DatabaseTask 中仲裁。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Service
public final class EasyDataLegacyMemoryStore implements ILegacyMemoryStore {
    private static final String DOMAIN = "agent.fact.legacy.v1";
    private final EasyDataMemoryNamespaceStore namespaces;
    private final EasyDataMemoryFactStore facts;
    private final MemoryFactRepository repository;
    private final ObjectMapper mapper;
    private final LegacyMemoryDecoder decoder;

    /**
     * 保存基础设施依赖，不访问旧行、配置或数据库。
     * @param namespaces 既有事务适配器
     * @param facts 新事实 schema 与提交边界
     * @param repository 旧表及映射仓库
     * @param mapper 存储转换器
     * @throws NullPointerException 当依赖为空时
     */
    @Autowired
    public EasyDataLegacyMemoryStore(EasyDataMemoryNamespaceStore namespaces, EasyDataMemoryFactStore facts,
            MemoryFactRepository repository, ObjectMapper mapper) {
        this.namespaces = Objects.requireNonNull(namespaces, "旧资料事务适配器不能为空。");
        this.facts = Objects.requireNonNull(facts, "新事实存储不能为空。");
        this.repository = Objects.requireNonNull(repository, "旧资料仓库不能为空。");
        this.mapper = Objects.requireNonNull(mapper, "旧资料转换器不能为空。");
        this.decoder = new LegacyMemoryDecoder(mapper);
    }

    /**
     * 有界读取明确的单行，原资料损坏不解释成不存在。
     * @param location 明确旧定位
     * @param fallbackRecordedAt 调用方固定的缺失时间估算
     * @return 原行快照或缺失
     * @throws NullPointerException 当参数为空时
     */
    @Override public CompletionStage<Optional<LegacyMemorySnapshot>> read(LegacyMemoryLocation location, Instant fallbackRecordedAt) {
        Objects.requireNonNull(location, "旧资料定位不能为空。"); Objects.requireNonNull(fallbackRecordedAt, "旧资料估算时间不能为空。");
        return namespaces.submit("预览旧记忆", () -> {
            var row = repository.findByNamespaceAndTargetAndKey("memory", location.target(), location.key());
            return row == null ? Optional.empty() : Optional.of(snapshot(row, location, fallbackRecordedAt));
        });
    }

    /**
     * 按 namespace、源行顺序持锁至提交；先检查全批映射，再执行任何事实写入。
     * 已提交请求只返回原回执，清空后旧回执不能绕过 generation；来源不被改写或删除。
     * @param command 明确且不可变的导入动作
     * @return 真实提交后的完整回执，拒绝时无部分导入
     * @throws NullPointerException 当命令为空时
     */
    @Override public CompletionStage<LegacyMemoryImportResult> importSnapshot(LegacyMemoryImport command) {
        Objects.requireNonNull(command, "旧记忆导入命令不能为空。");
        return namespaces.submit("导入旧记忆", () -> {
            facts.requireWriteGeneration(command.reference().namespaceId(), command.reference().generation());
            var operation = row("request", command.operationId().toString());
            if (operation != null) {
                var original = read(operation, OperationData.class);
                if (!original.command().operationId().equals(command.operationId())) { throw new IllegalStateException("旧导入回执的定位与操作不一致。"); }
                return original.command().equals(command) ? original.result() : LegacyMemoryImportResult.rejected(Status.CONFLICT);
            }
            var location = command.location(); var source = repository.lockLegacyProfile(location.target(), location.key());
            if (source == null) { return LegacyMemoryImportResult.rejected(Status.SOURCE_MISSING); }
            requireLocation(source, location);
            if (source.getVersion() != command.sourceVersion() || !LegacyMemoryDecoder.digest(source.getValue()).equals(command.sourceDigest())) {
                return LegacyMemoryImportResult.rejected(Status.SOURCE_CHANGED);
            }
            var snapshot = snapshot(source, location, command.fallbackRecordedAt());
            var records = new ArrayList<MemoryFactRecord>(); var pending = new ArrayList<MappingData>();
            for (var entry : snapshot.entries()) {
                var subject = command.subjects().get(entry.subjectKey());
                if (subject == null) { throw new IllegalArgumentException("旧记忆主体缺少明确的 TeaNeko UUID 映射。"); }
                var mapping = row("mapping", mappingKey(location, entry.id()));
                if (mapping != null) {
                    var original = read(mapping, MappingData.class); var record = original.record();
                    if (!mappingKey(original.location(), original.entry().id()).equals(mapping.getKey())) { throw new IllegalStateException("旧记忆映射的物理键与原证据不一致。"); }
                    if (!original.location().equals(location) || !original.entry().equals(entry) || !record.subjectId().equals(subject)
                            || !record.namespaceId().equals(command.reference().namespaceId()) || record.generation() != command.reference().generation()) {
                        return LegacyMemoryImportResult.rejected(Status.CONFLICT);
                    }
                    if (!record.equals(facts.findInTransaction(record.namespaceId(), record.generation(), record.id()))) {
                        throw new IllegalStateException("旧记忆映射引用的事实缺失或已改变，不能自动恢复。");
                    }
                    records.add(record);
                } else {
                    var record = new MemoryFactRecord(recordId(command, entry.id()), command.reference().namespaceId(), command.reference().generation(), 0,
                            subject, entry.value(), command.source(), entry.createdAt(), entry.updatedAt(), entry.locked(), entry.provenance());
                    pending.add(new MappingData(1, location, entry, record)); records.add(record);
                }
            }
            for (var mapping : pending) {
                facts.insertImportedInTransaction(mapping.record()); write("mapping", mappingKey(location, mapping.entry().id()), mapping);
            }
            var result = new LegacyMemoryImportResult(Status.IMPORTED, records, snapshot.skipped());
            write("request", command.operationId().toString(), new OperationData(1, command, result)); return result;
        });
    }

    /**
     * 核对实际物理定位，数据库排序规则不能把大小写或别名相近的其他行当作本来源。
     * @param row 当前源行
     * @param location 明确定位
     * @param fallbackRecordedAt 固定估算时刻
     * @return 已验证原行预览
     * @throws IllegalStateException 当仓库返回另一物理定位时
     * @throws IllegalArgumentException 当旧资料损坏或超限时
     */
    private LegacyMemorySnapshot snapshot(MemoryFactDataObject row, LegacyMemoryLocation location, Instant fallbackRecordedAt) {
        requireLocation(row, location);
        return decoder.decode(location, row.getVersion(), row.getValue(), fallbackRecordedAt);
    }

    /**
     * 对照真实行校验旧定位，不用数据库匹配结果推导主体或来源。
     * @param row 原行
     * @param location 预期定位
     * @throws IllegalStateException 当实际定位不一致时
     */
    private static void requireLocation(MemoryFactDataObject row, LegacyMemoryLocation location) {
        if (!row.getNamespace().equals("memory") || !row.getTarget().equals(location.target()) || !row.getKey().equals(location.key())) {
            throw new IllegalStateException("旧记忆行的实际物理定位与请求不一致。");
        }
    }

    /**
     * 全局绑定每条旧证据，防止更换请求 ID 后重导到其他空间或 clear 后的新代次。
     * @param location 旧物理定位
     * @param id 原记录 ID
     * @return 无分隔歧义的稳定摘要键
     */
    private String mappingKey(LegacyMemoryLocation location, String id) {
        return LegacyMemoryDecoder.digest(encode(List.of("memory", location.target(), location.key(), id)));
    }

    /**
     * 由物理来源及目标代次派生记录 ID，不使用随机数或导入时钟。
     * @param command 原导入命令
     * @param id 旧记录标识
     * @return 稳定新记录 UUID，实际插入仍验证唯一键
     */
    private UUID recordId(LegacyMemoryImport command, String id) {
        return UUID.nameUUIDFromBytes(encode(List.of(DOMAIN, mappingKey(command.location(), id), command.reference().namespaceId().value(),
                command.reference().generation())).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 按单一稳定键读取迁移账本，不扫描全部旧数据。
     * @param target 迁移数据种类
     * @param key 稳定键
     * @return 原事务行或空
     */
    private @Nullable MemoryFactDataObject row(String target, String key) { return repository.findByNamespaceAndTargetAndKey(DOMAIN, target, key); }

    /**
     * 解码并校验显式迁移结构，不泄露原始正文或异常链。
     * @param row 数据行
     * @param type 明确结构
     * @param <T> 结构类型
     * @return 不可变账本
     * @throws IllegalStateException 当数据损坏或版本不支持时
     */
    private <T> T read(MemoryFactDataObject row, Class<T> type) {
        try { return Objects.requireNonNull(mapper.readValue(row.getValue(), type)); }
        catch (RuntimeException invalid) { throw new IllegalStateException("旧记忆迁移账本损坏或版本不受支持。"); }
    }

    /**
     * 仅插入新稳定键，唯一冲突由外层事务重读，不覆写历史映射。
     * @param target 数据种类
     * @param key 稳定键
     * @param value 显式结构
     */
    private void write(String target, String key, Object value) { repository.saveAndFlush(repository.create(DOMAIN, target, key, encode(value))); }

    /**
     * 编码本地明确结构，错误不带原始资料。
     * @param value 待编码数据
     * @return JSON
     * @throws IllegalStateException 当编码失败时
     */
    private String encode(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (RuntimeException invalid) { throw new IllegalStateException("旧记忆迁移结构无法编码。"); }
    }

    /**
     * 单条来源的全局不可变转换映射，不因重试、目标变化或新代次覆盖。
     * @param schemaVersion 结构版本
     * @param location 原行
     * @param entry 原规范证据
     * @param record 首次导入结果
     * @author zExNocs
     * @date 2026/10/29
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private record MappingData(int schemaVersion, LegacyMemoryLocation location, LegacyMemorySnapshot.Entry entry, MemoryFactRecord record) {
        /**
         * 校验映射保留内容、时间、锁定及来源，没有静默改写旧证据。
         * @param schemaVersion 版本
         * @param location 原行
         * @param entry 原证据
         * @param record 导入记录
         * @throws IllegalArgumentException 当版本或映射不一致时
         */
        private MappingData {
            if (schemaVersion != 1 || location == null || entry == null || record == null || !entry.value().equals(record.value())
                    || !entry.createdAt().equals(record.createdAt()) || !entry.updatedAt().equals(record.updatedAt()) || entry.locked() != record.locked()
                    || !entry.provenance().equals(record.provenance()) || !entry.id().equals(entry.provenance().externalId())
                    || !location.target().equals(entry.provenance().target()) || !location.key().equals(entry.provenance().key())
                    || !LegacyMemoryDecoder.digest(entry.provenance().originalJson()).equals(entry.provenance().digest())) {
                throw new IllegalArgumentException("旧记忆映射结构或证据不一致。");
            }
        }
    }

    /**
     * 已提交请求的完整回执；拒绝不覆盖既有成功操作。
     * @param schemaVersion 版本
     * @param command 原命令
     * @param result 原结果
     * @author zExNocs
     * @date 2026/10/29
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private record OperationData(int schemaVersion, LegacyMemoryImport command, LegacyMemoryImportResult result) {
        /**
         * 验证结果属于原目标及显式主体映射，损坏回执不得作为成功重放。
         * @param schemaVersion 版本
         * @param command 原命令
         * @param result 原结果
         * @throws IllegalArgumentException 当版本、结果或定位不一致时
         */
        private OperationData {
            if (schemaVersion != 1 || command == null || result == null || result.status() != Status.IMPORTED) { throw new IllegalArgumentException("旧记忆导入回执结构无效。"); }
            var ids = new HashSet<UUID>();
            for (var record : result.records()) {
                var provenance = record.provenance();
                if (!ids.add(record.id()) || !record.namespaceId().equals(command.reference().namespaceId()) || record.generation() != command.reference().generation()
                        || provenance == null || !provenance.target().equals(command.location().target()) || !provenance.key().equals(command.location().key())
                        || !command.subjects().containsValue(record.subjectId())) { throw new IllegalArgumentException("旧记忆导入回执与原定位不一致。"); }
            }
        }
    }
}
