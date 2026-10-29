package org.zexnocs.teanekoagent.memory.namespace;

import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.zexnocs.teanekoagent.adapter.memory.EasyDataMemoryNamespaceStore;
import org.zexnocs.teanekoagent.memory.fact.legacy.LegacyMemoryLocation;
import org.zexnocs.teanekocore.actuator.task.EmptyTaskResult;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import org.zexnocs.teanekocore.database.base.DatabaseTaskConfig;
import org.zexnocs.teanekocore.database.base.interfaces.IDatabaseService;
import org.zexnocs.teanekocore.database.base.interfaces.IDatabaseTaskConfig;
import org.zexnocs.teanekocore.database.easydata.BaseEasyDataObject;
import org.zexnocs.teanekocore.database.easydata.general.GeneralEasyDataObject;
import org.zexnocs.teanekocore.database.easydata.general.GeneralEasyDataRepository;
import org.zexnocs.teanekocore.logger.ILogger;
import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.UnaryOperator;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 模拟事务隔离、唯一键、Version 仲裁和提交回滚，用于离线验证真实适配器的事务合同。
 * 不替代 MySQL/JPA 实机测试；仓库访问仍由实际 EasyDataMemoryNamespaceStore 发起。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
final class NamespaceDatabaseFixture implements AutoCloseable {
    final GeneralEasyDataRepository repository = mock(GeneralEasyDataRepository.class);
    final IDatabaseService database = mock(IDatabaseService.class);
    final ObjectMapper mapper = new ObjectMapper();
    final EasyDataMemoryNamespaceStore store = new EasyDataMemoryNamespaceStore(repository, database, mapper);
    final MemoryFactRepository factRepository = mock(MemoryFactRepository.class);
    final MemoryNamespaceWriteRepository namespaceLocks = mock(MemoryNamespaceWriteRepository.class);
    final EasyDataMemoryFactStore facts = new EasyDataMemoryFactStore(store, namespaceLocks, factRepository, mapper);
    final EasyDataLegacyMemoryStore legacy = new EasyDataLegacyMemoryStore(store, facts, factRepository, mapper);
    final EasyDataMemoryRecallStore recall = new EasyDataMemoryRecallStore(store, namespaceLocks, facts);
    final AtomicReference<Runnable> beforeCommit = new AtomicReference<>(() -> {});
    final AtomicReference<Runnable> beforeNamespaceLock = new AtomicReference<>(() -> {});
    final AtomicReference<Runnable> beforeLegacyLock = new AtomicReference<>(() -> {});
    final AtomicReference<Runnable> afterFactPage = new AtomicReference<>(() -> {});
    final AtomicInteger retries = new AtomicInteger();
    final AtomicReference<String> failingWritePrefix = new AtomicReference<>();
    private final ILogger logger = mock(ILogger.class);
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<String, BaseEasyDataObject> committed = new HashMap<>();
    private final ConcurrentMap<String, ReentrantLock> rowLocks = new ConcurrentHashMap<>();
    private final ThreadLocal<Transaction> transaction = new ThreadLocal<>();
    private final AtomicLong sequence = new AtomicLong();

    /** 为每个数据库任务建立独立事务副本，提交前不让外部读到写入。 */
    NamespaceDatabaseFixture() {
        when(repository.create(anyString(), anyString(), anyString(), anyString())).thenCallRealMethod();
        when(repository.findByNamespaceAndTargetAndKey(anyString(), anyString(), anyString())).thenAnswer(call ->
                transaction.get().rows.get(key(call.getArgument(0), call.getArgument(1), call.getArgument(2))));
        when(repository.saveAndFlush(any())).thenAnswer(call -> {
            GeneralEasyDataObject row = call.getArgument(0); save(row); return row;
        });
        when(factRepository.create(anyString(), anyString(), anyString(), anyString())).thenCallRealMethod();
        when(factRepository.findByNamespaceAndTargetAndKey(anyString(), anyString(), anyString())).thenAnswer(call ->
                transaction.get().rows.get(key(call.getArgument(0), call.getArgument(1), call.getArgument(2))));
        when(factRepository.saveAndFlush(any())).thenAnswer(call -> { MemoryFactDataObject row = call.getArgument(0); save(row); return row; });
        when(factRepository.findByNamespaceAndTargetAndKeyGreaterThanOrderByKeyAsc(anyString(), anyString(), anyString(), any())).thenAnswer(call -> {
            String domain = call.getArgument(0), target = call.getArgument(1), after = call.getArgument(2); Pageable page = call.getArgument(3);
            var rows = transaction.get().rows.values().stream().filter(row -> row instanceof MemoryFactDataObject && row.getNamespace().equals(domain)
                    && row.getTarget().equals(target) && row.getKey().compareTo(after) > 0).sorted(Comparator.comparing(BaseEasyDataObject::getKey))
                    .limit(page.getPageSize()).map(row -> (MemoryFactDataObject) row).toList();
            afterFactPage.get().run(); return rows;
        });
        when(namespaceLocks.lockNamespace(anyString())).thenAnswer(call -> lockNamespace(call.getArgument(0)));
        // 用更严格的独占锁模拟共享快照与写入互斥，不据此声称验证了并发读性能。
        when(namespaceLocks.lockSnapshot(anyString())).thenAnswer(call -> snapshotNamespace(call.getArgument(0)));
        when(factRepository.lockLegacyProfile(anyString(), anyString())).thenAnswer(call -> {
            beforeLegacyLock.get().run();
            return (MemoryFactDataObject) lockRow(key("memory", call.getArgument(0), call.getArgument(1)));
        });
        when(database.__pushTask(any())).thenAnswer(call -> {
            IDatabaseTaskConfig task = call.getArgument(0);
            var result = new CompletableFuture<ITaskResult<Void>>();
            executor.execute(() -> execute(task, result));
            return new TaskFuture<>(logger, task.getName(), result);
        });
    }

    /**
     * 记录当前事务实际写入，不在提交前发布行。
     * @param row 待写行
     */
    private void save(BaseEasyDataObject row) {
        var failurePrefix = failingWritePrefix.get();
        if (failurePrefix != null && row.getTarget().startsWith(failurePrefix)) { throw new IllegalStateException("测试指定写入失败。"); }
        if (row.getId() == 0) { row.setId(sequence.incrementAndGet()); }
        var key = key(row.getNamespace(), row.getTarget(), row.getKey());
        transaction.get().rows.put(key, row); transaction.get().writes.add(key);
    }

    /**
     * 模拟锁定查询读取最新已提交值，锁持有至当前事务结束，避免快照读掩盖换代。
     * @param id 业务空间
     * @return 锁定的当前行或空
     */
    private GeneralEasyDataObject lockNamespace(String id) {
        beforeNamespaceLock.get().run();
        return (GeneralEasyDataObject) lockRow(key("agent.namespace.v1", "namespace", id));
    }

    /**
     * 模拟共享锁之后首次普通查询才建立一致性视图，等待锁期间已提交的事实必须可见。
     * @param id 业务空间
     * @return 当前锁定元数据或空
     */
    private @Nullable GeneralEasyDataObject snapshotNamespace(String id) {
        var metadata = lockNamespace(id); var prefix = "record:" + id + ":g:";
        synchronized (this) {
            var current = transaction.get();
            var keys = current.rows.entrySet().stream().filter(entry -> entry.getValue().getNamespace().equals("agent.fact.v1")
                    && entry.getValue().getTarget().startsWith(prefix)).map(Map.Entry::getKey).toList();
            keys.forEach(key -> { current.rows.remove(key); current.versions.remove(key); });
            committed.forEach((key, row) -> {
                if (row.getNamespace().equals("agent.fact.v1") && row.getTarget().startsWith(prefix)) {
                    current.rows.put(key, copy(row)); current.versions.put(key, row.getVersion());
                }
            });
        }
        return metadata;
    }

    /**
     * 对两张表统一模拟当前读，持锁期间外部更新不能穿透提交。
     * @param locator 稳定物理键
     * @return 最新事务行或空
     */
    private @Nullable BaseEasyDataObject lockRow(String locator) {
        acquire(transaction.get(), locator);
        synchronized (this) {
            var committedRow = committed.get(locator);
            if (committedRow == null) { transaction.get().rows.remove(locator); transaction.get().versions.remove(locator); return null; }
            var row = copy(committedRow);
            transaction.get().rows.put(locator, row); transaction.get().versions.put(locator, row.getVersion()); return row;
        }
    }

    /**
     * 在提交监视器外取得行锁，避免与持锁事务提交形成测试死锁。
     * @param current 当前事务
     * @param locator 稳定行键
     */
    private void acquire(Transaction current, String locator) {
        var rowLock = rowLocks.computeIfAbsent(locator, ignored -> new ReentrantLock());
        if (!current.locks.contains(rowLock)) { rowLock.lock(); current.locks.add(rowLock); }
    }

    /**
     * 独立尝试重读快照，事务业务仅在最终提交后完成；重试上限与默认数据库配置一致。
     * @param task 实际适配器创建的数据库任务
     * @param result 最终完成信号
     */
    private void execute(IDatabaseTaskConfig task, CompletableFuture<ITaskResult<Void>> result) {
        for (int attempt = 0; attempt <= 8; attempt++) {
            try {
                var current = snapshot(); transaction.set(current);
                for (var operation : task.__getTasksWithTransaction()) { operation.call(); }
                beforeCommit.get().run();
                current.writes.stream().sorted().forEach(locator -> acquire(current, locator));
                commit(current);
                for (var operation : task.__getTasksWithCache()) { operation.call(); }
                result.complete(EmptyTaskResult.INSTANCE); return;
            } catch (ObjectOptimisticLockingFailureException | DataIntegrityViolationException conflict) {
                retries.incrementAndGet();
                if (attempt == 8) { result.completeExceptionally(conflict); }
            } catch (Exception failure) { result.completeExceptionally(failure); return; }
            finally {
                var current = transaction.get();
                if (current != null) { for (int i = current.locks.size() - 1; i >= 0; i--) { current.locks.get(i).unlock(); } }
                transaction.remove();
            }
        }
    }

    /**
     * 在提交锁下复制已提交数据，事务之间不共享可变 JPA 行。
     * @return 本次事务的初始视图
     */
    private synchronized Transaction snapshot() {
        var rows = new HashMap<String, BaseEasyDataObject>();
        committed.forEach((key, row) -> rows.put(key, copy(row)));
        var versions = new HashMap<String, Long>(); committed.forEach((key, row) -> versions.put(key, row.getVersion()));
        return new Transaction(rows, versions, new HashSet<>(), new ArrayList<>());
    }

    /**
     * 先核对所有写入版本，再一次发布全部行，失败不得部分提交。
     * @param current 本次事务候选
     * @throws DataIntegrityViolationException 当首次插入撞到唯一键时
     * @throws ObjectOptimisticLockingFailureException 当原始 Version 已变化时
     */
    private synchronized void commit(Transaction current) {
        for (var key : current.writes) {
            var previous = committed.get(key); var expected = current.versions.get(key);
            if (expected == null && previous != null) { throw new DataIntegrityViolationException("测试唯一键冲突"); }
            if (expected != null && (previous == null || previous.getVersion() != expected)) {
                throw new ObjectOptimisticLockingFailureException(GeneralEasyDataObject.class, key);
            }
        }
        for (var key : current.writes) {
            var row = copy(current.rows.get(key)); var previous = committed.get(key);
            row.setVersion(previous == null ? 0 : previous.getVersion() + 1); committed.put(key, row);
        }
    }

    /**
     * 复制数据及其 Version，模拟独立持久化上下文。
     * @param source 原行
     * @return 独立可变行
     */
    private BaseEasyDataObject copy(BaseEasyDataObject source) {
        var result = source instanceof MemoryFactDataObject ? factRepository.create(source.getNamespace(), source.getTarget(), source.getKey(), source.getValue())
                : repository.create(source.getNamespace(), source.getTarget(), source.getKey(), source.getValue());
        result.setId(source.getId()); result.setVersion(source.getVersion()); return result;
    }

    /**
     * 创建没有分隔符歧义的测试定位，三个字段分别编码。
     * @param namespace 物理 namespace
     * @param target 物理目标
     * @param key 物理键
     * @return 测试容器键
     */
    private static String key(String namespace, String target, String key) {
        return namespace.length() + ":" + namespace + target.length() + ":" + target + key.length() + ":" + key;
    }

    /**
     * 注入已有空间原始数据，用于损坏、未来 schema 和版本耗尽测试。
     * @param id 业务定位
     * @param json 原始存储正文
     */
    synchronized void seedNamespace(MemoryNamespaceId id, String json) {
        var row = repository.create("agent.namespace.v1", "namespace", id.value(), json);
        row.setId(sequence.incrementAndGet()); committed.put(key(row.getNamespace(), row.getTarget(), row.getKey()), row);
    }

    /**
     * 注入个人索引损坏，不触碰生产数据库。
     * @param creator 索引主体
     * @param json 存储正文
     */
    synchronized void seedCreator(UUID creator, String json) {
        var row = repository.create("agent.namespace.v1", "creator:" + creator, "owned", json);
        row.setId(sequence.incrementAndGet()); committed.put(key(row.getNamespace(), row.getTarget(), row.getKey()), row);
    }

    /**
     * 只在离线容器删除指定元数据行，模拟显式绑定目标丢失，不清理任何真实数据。
     * @param id 目标空间
     */
    synchronized void removeNamespace(MemoryNamespaceId id) {
        committed.remove(key("agent.namespace.v1", "namespace", id.value()));
    }

    /**
     * 修改测试域中指定前缀的存储正文，验证未来 schema 和定位损坏不被默认值掩盖。
     * @param targetPrefix 物理目标前缀
     * @param replacement 测试正文替换函数
     */
    synchronized void rewriteRows(String targetPrefix, UnaryOperator<String> replacement) {
        committed.values().stream().filter(row -> row.getTarget().startsWith(targetPrefix))
                .forEach(row -> row.setValue(replacement.apply(row.getValue())));
    }

    /**
     * 注入旧表原始行以验证新格式不会覆盖兼容数据，也可制造显式损坏行。
     * @param domain 原物理域
     * @param target 原物理目标
     * @param key 原键
     * @param value 原正文
     */
    synchronized void seedFactRow(String domain, String target, String key, String value) {
        var row = factRepository.create(domain, target, key, value); row.setId(sequence.incrementAndGet());
        committed.put(key(domain, target, key), row);
    }

    /**
     * 仅在离线容器移除指定事实行，验证映射不能把丢失证据自动重建。
     * @param namespace 物理域
     * @param target 物理目标
     * @param key 稳定键
     */
    synchronized void removeFactRow(String namespace, String target, String key) { committed.remove(key(namespace, target, key)); }

    /**
     * 模拟旧列表写入的事务更新，提交时同样受物理行锁和 Version 保护。
     * @param location 原行定位
     * @param json 新原文
     * @return 事务完成信号
     */
    CompletionStage<Void> updateLegacy(LegacyMemoryLocation location, String json) {
        return new DatabaseTaskConfig(database, "测试旧资料更新").addTransactionTask(() -> {
            var row = Objects.requireNonNull(factRepository.findByNamespaceAndTargetAndKey("memory", location.target(), location.key()));
            row.setValue(json); factRepository.saveAndFlush(row);
        }).pushAsync();
    }

    /**
     * 仅供离线断言观察已提交正文，不通过真实数据库或业务缓存读写。
     * @param domain 物理域
     * @param target 物理目标
     * @param key 稳定键
     * @return 原正文，不存在时为空
     */
    synchronized @Nullable String storedValue(String domain, String target, String key) {
        var row = committed.get(key(domain, target, key)); return row == null ? null : row.getValue();
    }

    /** 关闭测试拥有的执行器，等待在途事务完成，不关闭任何共享基础设施。 */
    @Override public void close() { executor.close(); }

    /**
     * 一次尝试的数据副本及原始版本，不暴露给业务组件。
     * @param rows 当前事务视图
     * @param versions 初始版本
     * @param writes 实际写入键
     * @param locks 持有至事务结束的测试行锁
     * @author zExNocs
     * @date 2026/10/29
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private record Transaction(Map<String, BaseEasyDataObject> rows, Map<String, Long> versions, Set<String> writes, List<ReentrantLock> locks) {}
}
