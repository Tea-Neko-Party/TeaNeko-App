package org.zexnocs.teanekoagent.adapter.memory;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.memory.namespace.IMemoryNamespaceStore;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespace;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceId;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceMutation;
import org.zexnocs.teanekocore.database.base.DatabaseTaskConfig;
import org.zexnocs.teanekocore.database.base.interfaces.IDatabaseService;
import org.zexnocs.teanekocore.database.easydata.general.GeneralEasyDataObject;
import org.zexnocs.teanekocore.database.easydata.general.GeneralEasyDataRepository;
import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceMutation.Status.*;

/**
 * 复用 general_data 保存空间注册元数据，不读取或重写旧 llm_related 事实和人格数据。
 * 每次重试在 DatabaseTask 的新事务中重读行，以唯一键和已有 Version 仲裁创建及配额索引。
 * 不使用 EasyData DTO 缓存或无版本批量更新，提交成功后才公开业务结果。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Service
public final class EasyDataMemoryNamespaceStore implements IMemoryNamespaceStore {
    private static final String STORAGE_NAMESPACE = "agent.namespace.v1";
    private final GeneralEasyDataRepository repository;
    private final IDatabaseService database;
    private final ObjectMapper mapper;

    /**
     * 保存现有基础设施，不在 Spring 构造期间查询或登记数据。
     * @param repository 通用 EasyData 仓库
     * @param database 既有事务及重试任务入口
     * @param mapper 本地存储 JSON 转换器
     * @throws NullPointerException 当依赖为空时
     */
    public EasyDataMemoryNamespaceStore(GeneralEasyDataRepository repository, IDatabaseService database, ObjectMapper mapper) {
        this.repository = Objects.requireNonNull(repository, "记忆空间仓库不能为空。");
        this.database = Objects.requireNonNull(database, "记忆空间数据库任务入口不能为空。");
        this.mapper = Objects.requireNonNull(mapper, "记忆空间 JSON 转换器不能为空。");
    }

    /**
     * 事务内按稳定键读取，不以缺失或损坏数据构造默认空间。
     * @param id 空间标识
     * @return 提交结束后的只读元数据
     * @throws NullPointerException 当空间标识为空时
     */
    @Override public CompletionStage<Optional<MemoryNamespace>> find(MemoryNamespaceId id) {
        Objects.requireNonNull(id, "记忆空间标识不能为空。");
        return submit("读取记忆空间", () -> {
            var row = namespaceRow(id);
            return row == null ? Optional.empty() : Optional.of(namespace(row, id));
        });
    }

    /**
     * 对初始候选执行配额及创建来源仲裁，真实数据库冲突交由原任务阶段重试。
     * @param candidate 初始空间
     * @param maxCreated 个人配额
     * @return 提交后的创建回执
     * @throws NullPointerException 当候选为空时
     * @throws IllegalArgumentException 当候选不是初始状态或配额无效时
     */
    @Override public CompletionStage<MemoryNamespaceMutation> create(MemoryNamespace candidate, int maxCreated) {
        Objects.requireNonNull(candidate, "记忆空间创建候选不能为空。");
        if (candidate.revision() != 0 || candidate.generation() != 0 || maxCreated < 1) {
            throw new IllegalArgumentException("记忆空间创建必须使用初始版本和正整数配额。");
        }
        return submit("登记记忆空间", () -> createInTransaction(candidate, maxCreated));
    }

    /**
     * 同一事务登记空间和有版本保护的创建者索引，供同存储域绑定适配器复用，失败时一起回滚。
     * @param candidate 初始候选
     * @param maxCreated 本次固定配额
     * @return 本次尝试的业务回执，外层仅在提交成功后交付
     */
    MemoryNamespaceMutation createInTransaction(MemoryNamespace candidate, int maxCreated) {
        var existing = namespaceRow(candidate.id());
        if (existing != null) {
            var current = namespace(existing, candidate.id());
            return new MemoryNamespaceMutation(Objects.equals(current.creation(), candidate.creation()) ? UNCHANGED : CONFLICT, current);
        }
        var creation = candidate.creation();
        GeneralEasyDataObject indexRow = null;
        CreatorIndex index = null;
        if (creation != null) {
            var creator = creation.actor().userId().orElseThrow();
            indexRow = creatorRow(creator);
            index = indexRow == null ? new CreatorIndex(1, creator, List.of()) : creatorIndex(indexRow, creator);
            if (index.ids().size() >= maxCreated) { return new MemoryNamespaceMutation(QUOTA_EXCEEDED, null); }
        }
        write(null, "namespace", candidate.id().value(), new NamespaceData(1, candidate));
        if (index != null) {
            var ids = new ArrayList<>(index.ids()); ids.add(candidate.id());
            write(indexRow, "creator:" + index.creator(), "owned", new CreatorIndex(1, index.creator(), ids));
        }
        return new MemoryNamespaceMutation(CREATED, candidate);
    }

    /**
     * 按创建者的受控索引分页读取，每页验证实际记录归属，不遍历整个表。
     * @param creator 真实创建者 UUID
     * @param offset 页面偏移
     * @param limit 一至一百的页面大小
     * @return 不可变页面
     * @throws NullPointerException 当创建者为空时
     * @throws IllegalArgumentException 当分页参数无效时
     */
    @Override public CompletionStage<List<MemoryNamespace>> listCreated(UUID creator, int offset, int limit) {
        Objects.requireNonNull(creator, "记忆空间创建者不能为空。");
        if (offset < 0 || limit < 1 || limit > 100) { throw new IllegalArgumentException("记忆空间页面偏移必须非负，页面大小必须为一至一百。"); }
        return submit("列举自建记忆空间", () -> {
            var row = creatorRow(creator);
            if (row == null) { return List.of(); }
            var ids = creatorIndex(row, creator).ids();
            if (offset >= ids.size()) { return List.of(); }
            var page = new ArrayList<MemoryNamespace>();
            for (var id : ids.subList(offset, offset + Math.min(limit, ids.size() - offset))) {
                var value = namespace(Objects.requireNonNull(namespaceRow(id), "记忆空间创建索引引用了缺失记录。"), id);
                var source = value.creation();
                if (source == null || !source.actor().userId().orElseThrow().equals(creator)) {
                    throw new IllegalStateException("记忆空间创建索引与实际来源不一致。");
                }
                page.add(value);
            }
            return List.copyOf(page);
        });
    }

    /**
     * 按原始预期版本推进数据代次；重试原请求只确认已完成变更，绝不再次推进。
     * @param id 目标空间
     * @param expectedRevision 原始预期版本
     * @param requestId 固定管理请求 ID
     * @return 提交后的代次回执
     * @throws NullPointerException 当目标或请求为空时
     * @throws IllegalArgumentException 当版本为负时
     */
    @Override public CompletionStage<MemoryNamespaceMutation> advanceGeneration(MemoryNamespaceId id, long expectedRevision, UUID requestId) {
        Objects.requireNonNull(id, "记忆空间标识不能为空。"); Objects.requireNonNull(requestId, "换代请求标识不能为空。");
        if (expectedRevision < 0) { throw new IllegalArgumentException("记忆空间预期版本不能为负。"); }
        return submit("推进记忆空间数据代次", () -> {
            var row = namespaceRow(id);
            if (row == null) { return new MemoryNamespaceMutation(NOT_FOUND, null); }
            var current = namespace(row, id);
            if (requestId.equals(current.lastGenerationRequest())) {
                return new MemoryNamespaceMutation(expectedRevision < Long.MAX_VALUE && current.revision() == expectedRevision + 1 ? UNCHANGED : CONFLICT, current);
            }
            if (current.revision() != expectedRevision) { return new MemoryNamespaceMutation(CONFLICT, current); }
            var changed = current.nextGeneration(requestId);
            write(row, "namespace", id.value(), new NamespaceData(1, changed));
            return new MemoryNamespaceMutation(GENERATION_ADVANCED, changed);
        });
    }

    /**
     * 只按业务空间键查询；方法必须在本次 DatabaseTask 事务内调用。
     * @param id 目标空间
     * @return 当前受 Version 管理的行或空
     */
    @Nullable GeneralEasyDataObject namespaceRow(MemoryNamespaceId id) {
        return row("namespace", id.value());
    }

    /**
     * 在当前事务内查询本适配域的数据行，不向业务服务公开 JPA 对象。
     * @param target 物理目标
     * @param key 物理键
     * @return 受 Version 管理的原始行或空
     */
    @Nullable GeneralEasyDataObject row(String target, String key) {
        return repository.findByNamespaceAndTargetAndKey(STORAGE_NAMESPACE, target, key);
    }

    /**
     * 定位个人配额索引；缺失时首次插入仍由数据库唯一键仲裁。
     * @param creator 真实创建者
     * @return 有版本行或空
     */
    private @Nullable GeneralEasyDataObject creatorRow(UUID creator) {
        return repository.findByNamespaceAndTargetAndKey(STORAGE_NAMESPACE, "creator:" + creator, "owned");
    }

    /**
     * 校验行与显式 schema 的定位一致，损坏数据不能当成空记录覆盖。
     * @param row 数据库行
     * @param id 预期空间
     * @return 通过校验的业务元数据
     * @throws IllegalStateException 当 JSON 或定位无效时
     */
    MemoryNamespace namespace(GeneralEasyDataObject row, MemoryNamespaceId id) {
        var value = read(row, NamespaceData.class).value();
        if (!value.id().equals(id)) { throw new IllegalStateException("记忆空间存储键与元数据不一致。"); }
        return value;
    }

    /**
     * 校验创建索引的主体与定位一致，不把其他用户的列表作为当前配额。
     * @param row 索引行
     * @param creator 预期创建者
     * @return 不可变索引
     * @throws IllegalStateException 当索引主体不一致时
     */
    private CreatorIndex creatorIndex(GeneralEasyDataObject row, UUID creator) {
        var value = read(row, CreatorIndex.class);
        if (!value.creator().equals(creator)) { throw new IllegalStateException("记忆空间创建索引主体不一致。"); }
        return value;
    }

    /**
     * 反序列化显式 schema，不向上层异常链复制存储正文。
     * @param row 数据行
     * @param type 已声明的存储类型
     * @param <T> 存储数据类型
     * @return 已构造并校验的数据
     * @throws IllegalStateException 当存储损坏或 schema 不受支持时
     */
    <T> T read(GeneralEasyDataObject row, Class<T> type) {
        try { return Objects.requireNonNull(mapper.readValue(row.getValue(), type)); }
        catch (RuntimeException invalid) { throw new IllegalStateException("记忆空间存储数据损坏或版本不受支持。"); }
    }

    /**
     * 更新事务读取的原始实体以保留 Version，禁止 upsert 绕过乐观锁。
     * @param existing 已读取的行；首次登记为空
     * @param target 物理目标
     * @param key 物理键
     * @param value 显式存储数据
     * @throws IllegalStateException 当本地 JSON 编码失败时
     */
    void write(@Nullable GeneralEasyDataObject existing, String target, String key, Object value) {
        final String encoded;
        try { encoded = mapper.writeValueAsString(value); }
        catch (RuntimeException invalid) { throw new IllegalStateException("记忆空间数据无法编码。"); }
        var row = existing == null ? repository.create(STORAGE_NAMESPACE, target, key, encoded) : existing;
        row.setValue(encoded);
        repository.saveAndFlush(row);
    }

    /**
     * 每次数据库重试重新执行纯事务操作，只有最终提交完成后才公开本次结果。
     * @param name 固定中文任务名，不含用户内容
     * @param operation 只在事务域访问仓库的操作
     * @param <T> 非空业务结果类型
     * @return 只读完成阶段；取消返回视图不取消已提交事务
     */
    <T> CompletionStage<T> submit(String name, Supplier<T> operation) {
        try {
            var result = new AtomicReference<T>();
            var task = new DatabaseTaskConfig(database, name);
            task.addTransactionTask(() -> { result.set(null); result.set(operation.get()); });
            return task.pushAsync().thenApply(ignored -> Objects.requireNonNull(result.get(), "记忆空间事务未产生结果。"))
                    .toCompletableFuture().minimalCompletionStage();
        } catch (RuntimeException failure) { return CompletableFuture.failedStage(failure); }
    }

    /**
     * 明确版本的空间存储外壳，不序列化整个 ECS 实体或 Java 类名。
     * @param schemaVersion 存储结构版本
     * @param value 业务元数据
     * @author zExNocs
     * @date 2026/10/29
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private record NamespaceData(int schemaVersion, MemoryNamespace value) {
        /**
         * 拒绝未来或损坏版本，保留原行供显式迁移。
         * @param schemaVersion 存储版本
         * @param value 元数据
         * @throws IllegalArgumentException 当版本不受支持或元数据缺失时
         */
        private NamespaceData {
            if (schemaVersion != 1 || value == null) { throw new IllegalArgumentException("记忆空间存储版本或内容无效。"); }
        }
    }

    /**
     * 单个创建者的活动空间索引；受该行 Version 保护，空间记录与索引同事务提交。
     * @param schemaVersion 存储版本
     * @param creator TeaNeko UUID
     * @param ids 按登记顺序排列的活动空间
     * @author zExNocs
     * @date 2026/10/29
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private record CreatorIndex(int schemaVersion, UUID creator, List<MemoryNamespaceId> ids) {
        /**
         * 冻结索引并拒绝重复项或免配额共享空间混入个人索引。
         * @param schemaVersion 存储版本
         * @param creator 创建者 UUID
         * @param ids 活动空间标识
         * @throws NullPointerException 当创建者、索引或元素为空时
         * @throws IllegalArgumentException 当版本或索引内容无效时
         */
        private CreatorIndex {
            Objects.requireNonNull(creator, "空间索引创建者不能为空。"); ids = List.copyOf(ids);
            if (schemaVersion != 1 || new HashSet<>(ids).size() != ids.size() || ids.contains(MemoryNamespaceId.SHARED)) {
                throw new IllegalArgumentException("记忆空间创建索引版本或内容无效。");
            }
        }
    }
}
