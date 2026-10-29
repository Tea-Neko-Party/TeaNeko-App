package org.zexnocs.teanekoagent.adapter.memory;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.memory.fact.*;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceId;
import org.zexnocs.teanekocore.database.easydata.BaseEasyDataObject;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletionStage;

/**
 * 在既有 DatabaseTask 事务中保存独立事实、内容去重索引和完整操作回执。
 * 写入先锁定 namespace 元数据再校验 generation，原子提交后才交付；不使用 EasyData 列表缓存。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Service
public final class EasyDataMemoryFactStore implements IMemoryFactStore {
    private static final String DOMAIN = "agent.fact.v1";
    private final EasyDataMemoryNamespaceStore namespaces;
    private final MemoryNamespaceWriteRepository locks;
    private final MemoryFactRepository repository;
    private final ObjectMapper mapper;

    /**
     * 保存基础设施依赖，不查询存储或创建默认空间。
     * @param namespaces 已有元数据事务适配器
     * @param locks namespace 行锁仓库
     * @param repository 事实物理仓库
     * @param mapper 本地存储 JSON 转换器
     * @throws NullPointerException 当必需依赖为空时
     */
    public EasyDataMemoryFactStore(EasyDataMemoryNamespaceStore namespaces, MemoryNamespaceWriteRepository locks,
            MemoryFactRepository repository, ObjectMapper mapper) {
        this.namespaces = Objects.requireNonNull(namespaces, "事实元数据存储不能为空。");
        this.locks = Objects.requireNonNull(locks, "事实 namespace 提交锁不能为空。");
        this.repository = Objects.requireNonNull(repository, "事实仓库不能为空。");
        this.mapper = Objects.requireNonNull(mapper, "事实 JSON 转换器不能为空。");
    }

    /**
     * 以原操作 ID 重放不可变回执；完全重复内容保留原记录，新增来源由原命令回执审计。
     * @param command 固定追加动作
     * @param now 初次尝试的可信时刻，事务重试不重新取时
     * @return 真实事务提交后的结果，旧 generation 不读取旧成功回执绕过清空
     * @throws NullPointerException 当命令或时间为空时
     */
    @Override public CompletionStage<MemoryFactReceipt> append(MemoryFactAppend command, Instant now) {
        Objects.requireNonNull(command, "事实追加命令不能为空。"); Objects.requireNonNull(now, "事实记录时刻不能为空。");
        var id = command.reference().namespaceId(); var generation = command.reference().generation();
        var digest = digest(command);
        return namespaces.submit("追加长期事实", () -> {
            requireWriteGeneration(id, generation);
            var receiptRow = row(target("operation", id, generation), command.operationId().toString());
            if (receiptRow != null) {
                var original = read(receiptRow, OperationData.class);
                if (!original.command().operationId().equals(command.operationId())
                        || !original.command().reference().namespaceId().equals(id) || original.command().reference().generation() != generation) {
                    throw new IllegalStateException("事实操作回执的物理定位与原命令不一致。");
                }
                if (!original.command().equals(command)) { return new MemoryFactReceipt(MemoryFactReceipt.Status.CONFLICT, null); }
                validate(original.result().record(), id, generation, null);
                return original.result();
            }
            MemoryFactReceipt result;
            var duplicate = row(target("duplicate", id, generation), digest);
            if (duplicate != null) {
                var recordId = read(duplicate, DuplicateData.class).id();
                var record = record(Objects.requireNonNull(row(target("record", id, generation), recordId.toString()), "事实去重索引引用了缺失记录。"), id, generation, recordId);
                if (!record.subjectId().equals(command.subjectId()) || !record.value().equals(command.value())) { throw new IllegalStateException("事实去重索引与内容不一致。"); }
                result = new MemoryFactReceipt(MemoryFactReceipt.Status.DUPLICATE, record);
            } else {
                var record = new MemoryFactRecord(command.operationId(), id, generation, 0, command.subjectId(), command.value(), command.source(), now, now, false);
                write(target("record", id, generation), record.id().toString(), new FactData(1, record));
                write(target("duplicate", id, generation), digest, new DuplicateData(1, record.id()));
                result = new MemoryFactReceipt(MemoryFactReceipt.Status.STORED, record);
            }
            write(target("operation", id, generation), command.operationId().toString(), new OperationData(1, command, result));
            return result;
        });
    }

    /**
     * 在同一只读事务中验证当前代次并读取稳定键，不返回历史代次的证据。
     * @param namespaceId 空间
     * @param generation 预期代次
     * @param id 记录 ID
     * @return 当前代次原记录
     * @throws NullPointerException 当空间或 ID 为空时
     * @throws IllegalArgumentException 当代次为负时
     */
    @Override public CompletionStage<Optional<MemoryFactRecord>> find(MemoryNamespaceId namespaceId, long generation, UUID id) {
        validateQuery(namespaceId, generation); Objects.requireNonNull(id, "事实记录 ID 不能为空。");
        return namespaces.submit("读取长期事实", () -> {
            requireGeneration(namespaceId, generation);
            var row = row(target("record", namespaceId, generation), id.toString());
            return row == null ? Optional.empty() : Optional.of(record(row, namespaceId, generation, id));
        });
    }

    /**
     * 按稳定 ID 读取至多一百条证据，额外一行仅用于判断是否还有后续页。
     * @param namespaceId 空间
     * @param generation 预期代次
     * @param after 前页游标
     * @param limit 页面大小
     * @return 不自动按来源隔离或排除过期证据的扫描页
     * @throws NullPointerException 当空间为空时
     * @throws IllegalArgumentException 当代次或分页大小无效时
     */
    @Override public CompletionStage<MemoryFactPage> list(MemoryNamespaceId namespaceId, long generation, @Nullable UUID after, int limit) {
        validateQuery(namespaceId, generation);
        if (limit < 1 || limit > 100) { throw new IllegalArgumentException("事实页面大小必须为一至一百。"); }
        return namespaces.submit("分页扫描长期事实", () -> {
            requireGeneration(namespaceId, generation);
            return listInTransaction(namespaceId, generation, after, limit);
        });
    }

    /**
     * 复用原 schema 和有界分页；调用者必须已在当前事务确认 generation。
     * @param id 空间
     * @param generation 代次
     * @param after 前页游标
     * @param limit 一至一百的页大小
     * @return 当前事务内原记录页
     */
    MemoryFactPage listInTransaction(MemoryNamespaceId id, long generation, @Nullable UUID after, int limit) {
        var rows = repository.findByNamespaceAndTargetAndKeyGreaterThanOrderByKeyAsc(DOMAIN, target("record", id, generation),
                after == null ? "" : after.toString(), PageRequest.of(0, limit + 1));
        var records = rows.stream().limit(limit).map(row -> record(row, id, generation, null)).toList();
        return new MemoryFactPage(records, rows.size() > limit ? records.getLast().id() : null);
    }

    /**
     * 验证读取代次，空间缺失和旧引用不伪装为确定没有事实。
     * @param id 业务空间
     * @param generation 预期代次
     * @throws MemoryFactGenerationException 当空间缺失或代次失效时
     */
    private void requireGeneration(MemoryNamespaceId id, long generation) {
        var row = namespaces.namespaceRow(id);
        if (row == null) { throw new MemoryFactGenerationException(true); }
        if (namespaces.namespace(row, id).generation() != generation) { throw new MemoryFactGenerationException(false); }
    }

    /**
     * 在现有写事务中锁定当前 namespace，普通追加与显式导入共用提交边界。
     * @param id 业务空间
     * @param generation 预期代次
     * @throws MemoryFactGenerationException 当空间缺失或代次失效时
     */
    void requireWriteGeneration(MemoryNamespaceId id, long generation) {
        var row = locks.lockNamespace(id.value());
        if (row == null) { throw new MemoryFactGenerationException(true); }
        if (namespaces.namespace(row, id).generation() != generation) { throw new MemoryFactGenerationException(false); }
    }

    /**
     * 在调用者已持有 namespace 写锁的事务中读取原事实，不新开任务或重建缺失记录。
     * @param id 业务空间
     * @param generation 数据代次
     * @param recordId 记录 ID
     * @return 当前原记录或空
     */
    @Nullable MemoryFactRecord findInTransaction(MemoryNamespaceId id, long generation, UUID recordId) {
        var row = row(target("record", id, generation), recordId.toString());
        return row == null ? null : record(row, id, generation, recordId);
    }

    /**
     * 专用导入保留每个旧 ID 的时间和锁定状态；相同内容仍保留独立来源证据。
     * 内容索引只指向首条接受记录，后续普通追加继续复用既有去重协议。
     * @param imported 已验证且带原始证据的导入记录
     * @throws IllegalStateException 当来源缺失、稳定 ID 冲突或内容索引损坏时
     */
    void insertImportedInTransaction(MemoryFactRecord imported) {
        var id = imported.namespaceId(); var generation = imported.generation();
        if (imported.provenance() == null || findInTransaction(id, generation, imported.id()) != null) {
            throw new IllegalStateException("导入事实缺少原始证据或稳定 ID 已被使用。");
        }
        var digest = digest(imported.subjectId(), imported.value()); var duplicate = row(target("duplicate", id, generation), digest);
        if (duplicate != null) {
            var previous = findInTransaction(id, generation, read(duplicate, DuplicateData.class).id());
            if (previous == null || !previous.subjectId().equals(imported.subjectId()) || !previous.value().equals(imported.value())) {
                throw new IllegalStateException("导入事实的既有内容索引损坏。");
            }
        }
        write(target("record", id, generation), imported.id().toString(), new FactData(1, imported));
        if (duplicate == null) { write(target("duplicate", id, generation), digest, new DuplicateData(1, imported.id())); }
    }

    /**
     * 校验调用参数，不在无效参数下提交数据库任务。
     * @param id 空间
     * @param generation 代次
     * @throws NullPointerException 当空间为空时
     * @throws IllegalArgumentException 当代次为负时
     */
    private static void validateQuery(MemoryNamespaceId id, long generation) {
        Objects.requireNonNull(id, "事实空间不能为空。"); if (generation < 0) { throw new IllegalArgumentException("事实查询代次不能为负。"); }
    }

    /**
     * 统一无歧义的物理定位；来源 scope/user/Agent 不进入共享空间过滤条件。
     * @param kind 记录、内容索引或操作回执
     * @param id 业务空间
     * @param generation 数据代次
     * @return 稳定物理目标
     */
    private static String target(String kind, MemoryNamespaceId id, long generation) { return kind + ":" + id.value() + ":g:" + generation; }

    /**
     * 读取本域稳定键，不使用 DTO 缓存或全表扫描。
     * @param target 物理目标
     * @param key 稳定键
     * @return 事务原始行或空
     */
    private @Nullable MemoryFactDataObject row(String target, String key) { return repository.findByNamespaceAndTargetAndKey(DOMAIN, target, key); }

    /**
     * 检查显式 schema 及记录完整定位，避免物理键与正文身份不同。
     * @param row 数据行
     * @param id 预期空间
     * @param generation 预期代次
     * @param recordId 可选预期记录 ID
     * @return 不可变记录
     * @throws IllegalStateException 当定位或正文无效时
     */
    private MemoryFactRecord record(MemoryFactDataObject row, MemoryNamespaceId id, long generation, @Nullable UUID recordId) {
        var value = read(row, FactData.class).record(); validate(value, id, generation, recordId);
        if (!row.getNamespace().equals(DOMAIN) || !row.getTarget().equals(target("record", id, generation)) || !row.getKey().equals(value.id().toString())) {
            throw new IllegalStateException("事实记录的物理定位与正文不一致。");
        }
        return value;
    }

    /**
     * 校验记录属于预期空间和代次，操作回执不能引用外部空间的数据。
     * @param value 待验证记录
     * @param id 空间
     * @param generation 代次
     * @param recordId 可选预期 ID
     * @throws IllegalStateException 当记录缺失或定位不一致时
     */
    private static void validate(@Nullable MemoryFactRecord value, MemoryNamespaceId id, long generation, @Nullable UUID recordId) {
        if (value == null || !value.namespaceId().equals(id) || value.generation() != generation || recordId != null && !value.id().equals(recordId)) {
            throw new IllegalStateException("事实正文与空间、代次或记录 ID 不一致。");
        }
    }

    /**
     * 主语与全部内容参与完全重复判断，来源保留在各自操作回执，不能跨主体合并。
     * @param command 本次追加意图
     * @return 稳定内容摘要
     * @throws IllegalStateException 当摘要计算或编码失败时
     */
    private String digest(MemoryFactAppend command) {
        return digest(command.subjectId(), command.value());
    }

    /**
     * 为普通追加和导入使用同一内容摘要，来源、锁定和原始时间不参与内容合并判断。
     * @param subject 主体 UUID
     * @param value 完整内容
     * @return 稳定内容摘要
     * @throws IllegalStateException 当摘要计算或编码失败时
     */
    private String digest(UUID subject, MemoryFactValue value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsString(List.of(subject, value)).getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException | RuntimeException failure) { throw new IllegalStateException("事实内容摘要无法生成。"); }
    }

    /**
     * 解码显式结构，损坏或未来 schema 失败，不将原存储正文附加到异常。
     * @param row 原行
     * @param type 明确结构
     * @param <T> 数据类型
     * @return 构造并验证的数据
     * @throws IllegalStateException 当内容损坏或版本不受支持时
     */
    private <T> T read(BaseEasyDataObject row, Class<T> type) {
        try { return Objects.requireNonNull(mapper.readValue(row.getValue(), type)); }
        catch (RuntimeException invalid) { throw new IllegalStateException("事实存储数据损坏或版本不受支持。"); }
    }

    /**
     * 只插入稳定新键，唯一键冲突由 DatabaseTask 重新读取并仲裁，不覆盖原事实。
     * @param target 物理目标
     * @param key 稳定键
     * @param value 显式 schema
     * @throws IllegalStateException 当本地编码失败时
     */
    private void write(String target, String key, Object value) {
        final String encoded;
        try { encoded = mapper.writeValueAsString(value); }
        catch (RuntimeException failure) { throw new IllegalStateException("事实存储数据无法编码。"); }
        repository.saveAndFlush(repository.create(DOMAIN, target, key, encoded));
    }

    /**
     * 记录 schema，不序列化 Java 类型名或 ECS 容器。
     * @param schemaVersion 结构版本
     * @param record 原记录
     * @author zExNocs
     * @date 2026/10/29
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private record FactData(int schemaVersion, MemoryFactRecord record) {
        /**
         * 拒绝未知版本或无记录的伪成功。
         * @param schemaVersion 结构版本
         * @param record 原记录
         * @throws IllegalArgumentException 当结构版本或记录无效时
         */
        private FactData { if (schemaVersion != 1 || record == null) { throw new IllegalArgumentException("事实记录结构无效。"); } }
    }

    /**
     * 内容摘要的稳定记录定位，正文仍须验证以发现索引损坏。
     * @param schemaVersion 结构版本
     * @param id 首次接受的记录 ID
     * @author zExNocs
     * @date 2026/10/29
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private record DuplicateData(int schemaVersion, UUID id) {
        /**
         * 拒绝损坏索引。
         * @param schemaVersion 版本
         * @param id 原记录 ID
         * @throws IllegalArgumentException 当结构版本或 ID 无效时
         */
        private DuplicateData { if (schemaVersion != 1 || id == null) { throw new IllegalArgumentException("事实去重索引结构无效。"); } }
    }

    /**
     * 保留完整原命令及原回执，不让不同来源或内容重用同操作 ID。
     * @param schemaVersion 结构版本
     * @param command 原命令
     * @param result 原结果
     * @author zExNocs
     * @date 2026/10/29
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private record OperationData(int schemaVersion, MemoryFactAppend command, MemoryFactReceipt result) {
        /**
         * 仅持久化实际接受结果，冲突不覆盖原回执。
         * @param schemaVersion 结构版本
         * @param command 原命令
         * @param result 原结果
         * @throws IllegalArgumentException 当版本、命令或结果无效时
         */
        private OperationData {
            if (schemaVersion != 1 || command == null || result == null || result.status() == MemoryFactReceipt.Status.CONFLICT) { throw new IllegalArgumentException("事实操作回执结构无效。"); }
            var record = result.record();
            validate(record, command.reference().namespaceId(), command.reference().generation(), null);
            if (record == null || !record.subjectId().equals(command.subjectId()) || !record.value().equals(command.value())
                    || result.status() == MemoryFactReceipt.Status.STORED && (!record.id().equals(command.operationId()) || !record.source().equals(command.source()))) {
                throw new IllegalArgumentException("事实操作回执与原主语、内容或首次来源不一致。");
            }
        }
    }
}
