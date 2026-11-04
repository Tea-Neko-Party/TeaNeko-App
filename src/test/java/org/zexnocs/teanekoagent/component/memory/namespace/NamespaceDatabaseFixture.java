package org.zexnocs.teanekoagent.component.memory.namespace;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.zexnocs.teanekoagent.component.memory.adapter.EasyDataMemoryFactStore;
import org.zexnocs.teanekoagent.component.memory.adapter.EasyDataMemoryNamespaceStore;
import org.zexnocs.teanekoagent.component.memory.adapter.EasyDataMemoryRecallStore;
import org.zexnocs.teanekoagent.database.easydata.TeaNekoAgentEasyData;
import org.zexnocs.teanekoagent.database.easydata.TeaNekoAgentEasyDataObject;
import org.zexnocs.teanekoagent.database.easydata.TeaNekoAgentEasyDataRepository;
import org.zexnocs.teanekocore.actuator.task.EmptyTaskResult;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import org.zexnocs.teanekocore.cache.interfaces.ICacheService;
import org.zexnocs.teanekocore.database.base.interfaces.IDatabaseService;
import org.zexnocs.teanekocore.database.base.interfaces.IDatabaseTaskConfig;
import org.zexnocs.teanekocore.database.easydata.BaseEasyDataObject;
import org.zexnocs.teanekocore.database.easydata.core.EasyDataRepositoryScanner;
import org.zexnocs.teanekocore.database.easydata.core.EasyDataService;
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
 * 不替代 MySQL/JPA 实机测试；仓库访问由实际 EasyData 基础层发起。
 * <br>2026/11/03：复用固定域维护分页，保持真实事务、版本与回滚夹具。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.5.0-l
 * @version 4.8.0-l
 */
final class NamespaceDatabaseFixture implements AutoCloseable {
    final TeaNekoAgentEasyDataRepository repository = mock(TeaNekoAgentEasyDataRepository.class);
    final IDatabaseService database = mock(IDatabaseService.class);
    final ObjectMapper mapper = new ObjectMapper();
    final TeaNekoAgentEasyData data;
    final EasyDataService easyDataService;
    final EasyDataMemoryNamespaceStore store;
    final EasyDataMemoryFactStore facts;
    final EasyDataMemoryRecallStore recall;
    final AtomicReference<Runnable> beforeCommit = new AtomicReference<>(() -> {});
    final AtomicReference<Runnable> beforeNamespaceLock = new AtomicReference<>(() -> {});
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
                visible().get(key(call.getArgument(0), call.getArgument(1), call.getArgument(2))));
        when(repository.findByNamespaceAndTarget(anyString(), anyString())).thenAnswer(call -> visible().values().stream()
                .filter(row -> row.getNamespace().equals(call.getArgument(0)) && row.getTarget().equals(call.getArgument(1))).map(row -> (TeaNekoAgentEasyDataObject) copy(row)).toList());
        when(repository.saveAndFlush(any())).thenAnswer(call -> { TeaNekoAgentEasyDataObject row = call.getArgument(0); save(row); return row; });
        when(repository.save(any())).thenAnswer(call -> { TeaNekoAgentEasyDataObject row = call.getArgument(0); save(row); return row; });
        org.mockito.Mockito.doCallRealMethod().when(repository).updateDataObjectData(anyString(), anyString(), anyString(), anyString());
        org.mockito.Mockito.doAnswer(call -> {
            var locator = key(call.getArgument(0), call.getArgument(1), call.getArgument(2)); var current = transaction.get();
            if (current.rows.remove(locator) != null) { current.writes.add(locator); } return null;
        }).when(repository).deleteByNamespaceAndTargetAndKey(anyString(), anyString(), anyString());
        org.mockito.Mockito.doAnswer(call -> {
            BaseEasyDataObject row = call.getArgument(0); var locator = key(row.getNamespace(), row.getTarget(), row.getKey());
            var current = transaction.get(); if (current.rows.remove(locator) != null) { current.writes.add(locator); } return null;
        }).when(repository).delete(any(TeaNekoAgentEasyDataObject.class));
        when(repository.lockDataForWrite(anyString(), anyString(), anyString())).thenAnswer(call -> locked(call.getArgument(0), call.getArgument(1), call.getArgument(2), false));
        when(repository.lockDataForRead(anyString(), anyString(), anyString())).thenAnswer(call -> locked(call.getArgument(0), call.getArgument(1), call.getArgument(2), true));
        when(repository.scanData(anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.nullable(String.class), any())).thenAnswer(call ->
                page(call.getArgument(0), call.getArgument(1), call.getArgument(2), call.getArgument(3), call.getArgument(4), false));
        when(repository.scanDataForRead(anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.nullable(String.class), any())).thenAnswer(call ->
                page(call.getArgument(0), call.getArgument(1), call.getArgument(2), call.getArgument(3), call.getArgument(4), true));
        when(repository.scanDataForWrite(anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.nullable(String.class), any())).thenAnswer(call ->
                page(call.getArgument(0), call.getArgument(1), call.getArgument(2), call.getArgument(3), call.getArgument(4), true));
        when(repository.findByNamespaceAndTargetAndIdLessThanOrderByIdDesc(anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong(), any())).thenAnswer(call -> {
            String domain = call.getArgument(0), target = call.getArgument(1); long before = call.getArgument(2); Pageable page = call.getArgument(3);
            return transaction.get().rows.values().stream().filter(row -> row.getNamespace().equals(domain) && row.getTarget().equals(target) && row.getId() < before)
                    .sorted(Comparator.comparingLong(BaseEasyDataObject::getId).reversed()).limit(page.getPageSize()).map(row -> (TeaNekoAgentEasyDataObject) row).toList();
        });
        // 2026/11/03：域维护仍通过 Core 事务 DTO，不为新 Store 绕过仓库封装。
        when(repository.findByNamespaceAndIdLessThanOrderByIdDesc(anyString(), org.mockito.ArgumentMatchers.anyLong(), any())).thenAnswer(call -> {
            String domain = call.getArgument(0); long before = call.getArgument(1); Pageable page = call.getArgument(2);
            return transaction.get().rows.values().stream().filter(row -> row.getNamespace().equals(domain) && row.getId() < before)
                    .sorted(Comparator.comparingLong(BaseEasyDataObject::getId).reversed()).limit(page.getPageSize()).map(row -> (TeaNekoAgentEasyDataObject) row).toList();
        });
        when(database.__pushTask(any())).thenAnswer(call -> {
            IDatabaseTaskConfig task = call.getArgument(0); var result = new CompletableFuture<ITaskResult<Void>>();
            executor.execute(() -> execute(task, result)); return new TaskFuture<>(logger, task.getName(), result);
        });
        var scanner = mock(EasyDataRepositoryScanner.class);
        org.mockito.Mockito.doReturn(repository).when(scanner).getRepository(TeaNekoAgentEasyDataObject.class);
        easyDataService = new EasyDataService(mock(ICacheService.class), database, scanner, mapper);
        data = new TeaNekoAgentEasyData(easyDataService);
        store = new EasyDataMemoryNamespaceStore(data, mapper); facts = new EasyDataMemoryFactStore(store, data, mapper);
        recall = new EasyDataMemoryRecallStore(store, facts);
    }

    /**
     * 普通 DTO 只读取已提交副本，事务 DTO 使用当前尝试的隔离状态。
     * @return 当前可见行映射
     */
    private synchronized Map<String, BaseEasyDataObject> visible() { return transaction.get() == null ? new HashMap<>(committed) : transaction.get().rows; }

    /**
     * 模拟通用当前读，空间读取保留原有并发协调钩子。
     * @param domain 域
     * @param target 目标
     * @param id 键
     * @param read 是否共享读取
     * @return 当前实体或空
     */
    private BaseEasyDataObject locked(String domain, String target, String id, boolean read) {
        if (domain.equals("agent.namespace.v1") && target.equals("namespace")) { return read ? snapshotNamespace(id) : lockNamespace(id); }
        return lockRow(key(domain, target, id));
    }

    /**
     * 复用原有界扫描模型，不为每个业务 Store 重建不同仓库夹具。
     * @param domain 域
     * @param target 目标
     * @param after 排除下界
     * @param through 可选上界
     * @param page 有界页面
     * @param current 是否当前读
     * @return 固定目标页面
     */
    private List<TeaNekoAgentEasyDataObject> page(String domain, String target, String after, @Nullable String through, Pageable page, boolean current) {
        var rows = current ? currentFactPage(domain, target, after, page) : transaction.get().rows.values().stream()
                .filter(row -> row.getNamespace().equals(domain) && row.getTarget().equals(target) && row.getKey().compareTo(after) > 0)
                .sorted(Comparator.comparing(BaseEasyDataObject::getKey)).map(row -> (TeaNekoAgentEasyDataObject) row).toList();
        var result = rows.stream().filter(row -> through == null || row.getKey().compareTo(through) <= 0).limit(page.getPageSize()).toList();
        if (domain.equals("agent.fact.v1") && target.startsWith("record:")) { afterFactPage.get().run(); }
        return result;
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
     * 模拟管理锁定查询的当前读，保留本事务写入并刷新其他已提交行。
     * @param domain 事实域
     * @param target 固定目标
     * @param after 游标
     * @param page 有界分页
     * @return 当前可见原始页面
     */
    private synchronized List<TeaNekoAgentEasyDataObject> currentFactPage(String domain, String target, String after, Pageable page) {
        var current = transaction.get();
        current.rows.entrySet().removeIf(entry -> entry.getValue().getNamespace().equals(domain) && entry.getValue().getTarget().equals(target)
                && !current.writes.contains(entry.getKey()));
        committed.forEach((locator, row) -> {
            if (row.getNamespace().equals(domain) && row.getTarget().equals(target) && !current.writes.contains(locator)) {
                current.rows.put(locator, copy(row)); current.versions.put(locator, row.getVersion());
            }
        });
        return current.rows.values().stream().filter(row -> row instanceof TeaNekoAgentEasyDataObject && row.getNamespace().equals(domain)
                && row.getTarget().equals(target) && row.getKey().compareTo(after) > 0).sorted(Comparator.comparing(BaseEasyDataObject::getKey))
                .limit(page.getPageSize()).map(row -> (TeaNekoAgentEasyDataObject) row).toList();
    }

    /**
     * 模拟锁定查询读取最新已提交值，锁持有至当前事务结束，避免快照读掩盖换代。
     * @param id 业务空间
     * @return 锁定的当前行或空
     */
    private TeaNekoAgentEasyDataObject lockNamespace(String id) {
        beforeNamespaceLock.get().run();
        return (TeaNekoAgentEasyDataObject) lockRow(key("agent.namespace.v1", "namespace", id));
    }

    /**
     * 模拟共享锁之后首次普通查询才建立一致性视图，等待锁期间已提交的事实必须可见。
     * @param id 业务空间
     * @return 当前锁定元数据或空
     */
    private @Nullable TeaNekoAgentEasyDataObject snapshotNamespace(String id) {
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
                TransactionSynchronizationManager.setActualTransactionActive(true); TransactionSynchronizationManager.initSynchronization();
                for (var operation : task.__getTasksWithTransaction()) { operation.call(); }
                beforeCommit.get().run();
                current.writes.stream().sorted().forEach(locator -> acquire(current, locator));
                commit(current);
                TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
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
                if (TransactionSynchronizationManager.isSynchronizationActive()) { TransactionSynchronizationManager.clearSynchronization(); }
                TransactionSynchronizationManager.setActualTransactionActive(false);
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
                throw new ObjectOptimisticLockingFailureException(TeaNekoAgentEasyDataObject.class, key);
            }
        }
        for (var key : current.writes) {
            if (!current.rows.containsKey(key)) { committed.remove(key); continue; }
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
        var result = repository.create(source.getNamespace(), source.getTarget(), source.getKey(), source.getValue());
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
        var row = repository.create(domain, target, key, value); row.setId(sequence.incrementAndGet());
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
     * 验收当前已提交业务目标，不向业务服务暴露仓库。
     * @param domain 精确域
     * @param targetPrefix 目标前缀
     * @return 已提交正文快照
     */
    synchronized List<String> values(String domain, String targetPrefix) {
        return committed.values().stream().filter(row -> row.getNamespace().equals(domain) && row.getTarget().startsWith(targetPrefix))
                .map(BaseEasyDataObject::getValue).toList();
    }

    /**
     * 一次尝试的数据副本及原始版本，不暴露给业务组件。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param rows 当前事务视图
     * @param versions 初始版本
     * @param writes 实际写入键
     * @param locks 持有至事务结束的测试行锁
     * @author zExNocs
     * @date 2026/10/29
     * @since 4.5.0-l
     * @version 4.8.0-l
     */
    private record Transaction(Map<String, BaseEasyDataObject> rows, Map<String, Long> versions, Set<String> writes, List<ReentrantLock> locks) {}
}
