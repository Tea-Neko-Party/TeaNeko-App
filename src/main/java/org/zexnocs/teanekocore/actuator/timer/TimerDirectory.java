package org.zexnocs.teanekocore.actuator.timer;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekocore.actuator.runtime.RuntimeMonitor;
import org.zexnocs.teanekocore.actuator.timer.interfaces.ITimer;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 真实计时器的进程目录及暂停门禁，不改变计时器自身周期或已有生命周期对象。
 * 暂停只阻止下一次触发，不取消已经注册的任务。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：合并真实调度来源，按注解分类并在分页前筛选；关键任务保持只读。
 * <br>2026/11/05：分页复用专用来源缓存，不在页面轮询中发现 Bean。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.8.2-l
 */
@Component
public final class TimerDirectory {
    private final Map<ITimer<?>, Entry> timers = new ConcurrentHashMap<>();
    private final Map<UUID, Entry> ids = new ConcurrentHashMap<>();
    private final LinkedHashMap<UUID, Operation> operations = new LinkedHashMap<>();
    private final Map<String, String> categoryNames = new ConcurrentHashMap<>();
    private @Nullable TimerSourceScanner scanner;

    /**
     * 2026/11/05：只保存专用缓存目录，发现由初始化或手动重扫触发。
     * @param scanner 已装配的调度来源扫描器
     */
    @Autowired
    public void installSources(TimerSourceScanner scanner) {
        this.scanner = scanner;
    }

    /**
     * @param timer 原服务刚注册的计时器
     */
    public void register(ITimer<?> timer) {
        if (timers.size() >= 4096) {
            return;
        }
        timers.computeIfAbsent(timer, value -> {
            var entry = new Entry(value);
            // 2026/11/04：分类键的含义不能随注册顺序改变。
            checkCategory(entry.presentation.category(), entry.presentation.categoryName());
            ids.put(entry.id, entry);
            return entry;
        });
    }

    /**
     * @param timer 原服务已经清理的计时器
     */
    public void remove(ITimer<?> timer) {
        var entry = timers.remove(timer);
        if (entry != null) {
            ids.remove(entry.id, entry);
        }
    }

    /**
     * 与暂停动作使用同一门禁；回调仍在原 Timer 调度线程执行。
     * @param timer 原计时器
     * @param update 原生命周期检查、时间判定和同步触发
     */
    public void visit(ITimer<?> timer, Runnable update) {
        var entry = timers.get(timer);
        if (entry == null) {
            update.run();
            return;
        }
        entry.lock.lock();
        try {
            var life = timer.getTimerTaskConfig().getLivable();
            if (!entry.paused || life != null && !life.isAlive()) {
                update.run();
            }
        } finally {
            entry.lock.unlock();
        }
    }

    /**
     * @param after 排他 UUID 游标
     * @param limit 页大小，一至一百
     * @return 实际注册目录，目录变化时允许重新读取首页
     */
    public Page page(String after, int limit) {
        // 2026/11/04：旧入口保留无筛选行为。
        return page(after, limit, "", "", "", "");
    }

    /**
     * 2026/11/05：来源实例读取缓存，任务状态仍在每次页面查询实时取得。
     * @param after 当前进程排他 UUID 游标
     * @param limit 一至一百项
     * @param category 精确分类键，空值不限
     * @param kind 精确规则类型，空值不限
     * @param state 精确观测状态，空值不限
     * @param search 名称包含条件
     * @return 先筛选后分页的统一目录，分类来自本次完整观测窗口
     */
    public Page page(String after, int limit, String category, String kind, String state, String search) {
        if (limit < 1 || limit > 100 || after.length() > 36 || search.length() > 256
                || !category.isEmpty() && !category.matches("[a-z][a-z0-9_.-]{0,63}")
                || !kind.isEmpty() && !kind.matches("[A-Z][A-Z0-9_]{0,31}") || state.length() > 32) {
            throw new IllegalArgumentException("定时器分页参数无效。");
        }
        // 2026/11/04：来源只贡献观测，外部来源不能取得 Core 暂停能力。
        var observed = new ArrayList<>(ids.values().stream().filter(entry -> entry.presentation.visible())
                .map(this::view).toList());
        var sourceIds = new HashSet<String>();
        if (scanner != null) {
            for (var source : scanner.views()) {
                String id = source.source();
                if (!id.matches("[a-z][a-z0-9_.-]{0,63}") || id.equals("core") || !sourceIds.add(id)) {
                    throw new IllegalArgumentException("定时观测来源标识无效或重复。");
                }
                var contributed = source.snapshot(4097);
                if (contributed.size() > 4097 || contributed.stream().anyMatch(value ->
                        !value.source().equals(id) || !value.actions().isEmpty())) {
                    throw new IllegalArgumentException("外部调度来源只能贡献有界的只读观测。");
                }
                observed.addAll(contributed);
            }
        }
        boolean limited = timers.size() >= 4096 || observed.size() > 4096;
        var window = observed.stream().sorted(Comparator.comparing(View::id)).limit(4096).toList();
        var categories = new TreeMap<String, String>();
        var unique = new HashSet<UUID>();
        for (var item : window) {
            checkCategory(item.category(), item.categoryName());
            categories.put(item.category(), item.categoryName());
            if (!unique.add(item.id())) {
                throw new IllegalArgumentException("定时观测来源返回了重复任务标识。");
            }
        }
        String needle = search.toLowerCase(Locale.ROOT);
        var found = window.stream().filter(value -> category.isEmpty() || category.equals(value.category()))
                .filter(value -> kind.isEmpty() || kind.equals(value.rule().kind()))
                .filter(value -> state.isEmpty() || state.equals(value.state()))
                .filter(value -> value.name().toLowerCase(Locale.ROOT).contains(needle))
                .filter(value -> value.id().toString().compareTo(after) > 0)
                .sorted(Comparator.comparing(value -> value.id().toString())).limit(limit + 1L).toList();
        var items = found.stream().limit(limit).toList();
        return new Page(items, found.size() > limit, items.isEmpty() ? "" : items.getLast().id().toString(), limited,
                categories.entrySet().stream().map(value -> new Category(value.getKey(), value.getValue())).toList());
    }

    /**
     * @param id 稳定分类键
     * @param name 实际中文名称
     * @throws IllegalArgumentException 当不同来源为同一分类声明不同名称时
     */
    private void checkCategory(String id, String name) {
        if (!id.matches("[a-z][a-z0-9_.-]{0,63}") || name.isBlank() || name.length() > 64) {
            throw new IllegalArgumentException("定时观测来源返回了无效分类。");
        }
        String previous = categoryNames.putIfAbsent(id, name);
        if (previous != null && !previous.equals(name)) {
            throw new IllegalArgumentException("同一定时任务分类使用了不同名称：" + id);
        }
    }

    /**
     * @param entry 原计时器条目
     * @return 只读近时观测，未知来源状态不推定为活动
     */
    private View view(Entry entry) {
        try {
            var config = entry.timer.getTimerTaskConfig();
            var life = config.getLivable();
            var pause = config.getPausable();
            String state = life != null && !life.isAlive() ? "REMOVED" : entry.paused ? "PAUSED"
                    : pause != null && pause.isPaused() ? "SOURCE_PAUSED" : "ACTIVE";
            // 2026/11/04：关键任务的只读声明同时约束目录动作与实际写入口。
            var metadata = entry.presentation;
            return new View(entry.id, RuntimeMonitor.safe(metadata.name().isEmpty()
                    ? config.getTaskConfig().getName() : metadata.name(), 128), state,
                    entry.revision, entry.timer.observation(), state.equals("REMOVED") || !metadata.controllable()
                    ? List.of() : List.of(entry.paused ? "RESUME" : "PAUSE"), metadata.category(),
                    metadata.categoryName(), metadata.description(), "core");
        } catch (RuntimeException failure) {
            return new View(entry.id, "定时器状态暂不可用", "UNKNOWN", entry.revision,
                    new TimerObservation("CUSTOM", "规则暂不可用", null, false, ""), List.of(),
                    entry.presentation.category(), entry.presentation.categoryName(), entry.presentation.description(), "core");
        }
    }

    /**
     * 相同编号只重放同一参数；动作与下一次触发在同一门禁下仲裁。
     * @param id 当前进程的计时器标识
     * @param change 原版本和明确暂停状态
     * @param guard 副作用前当前授权
     * @return 原动作回执，不声称已经停止在途任务
     * @throws ConcurrentModificationException 当门禁版本已变化时
     * @throws SecurityException 当副作用前授权已经撤销时
     * @throws IllegalStateException 当门禁繁忙、来源不可管理或回执窗口已满时
     */
    public synchronized Receipt change(UUID id, Change change, Runnable guard) {
        guard.run();
        var old = operations.get(change.operationId());
        if (old != null) {
            if (!old.timer().equals(id) || !old.change().equals(change)) {
                throw new IllegalArgumentException("定时器操作编号已绑定其他参数。");
            }
            return old.receipt();
        }
        operations.values().removeIf(operation -> operation.receipt().at().isBefore(Instant.now().minusSeconds(86400)));
        if (operations.size() >= 256) {
            throw new IllegalStateException("定时器操作回执窗口已满。");
        }
        var entry = ids.get(id);
        if (entry == null) {
            throw new NoSuchElementException("定时器不在当前进程目录。");
        }
        if (!entry.lock.tryLock()) {
            throw new IllegalStateException("定时器正在触发，请保留原编号稍后重试。");
        }
        try {
            if (entry.revision != change.revision()) {
                throw new ConcurrentModificationException("定时器版本已经变化。");
            }
            if (view(entry).actions().isEmpty()) {
                throw new IllegalStateException("定时器当前不可管理。");
            }
            guard.run();
            if (entry.paused != change.paused()) {
                entry.paused = change.paused();
                entry.revision++;
            }
            var receipt = new Receipt(change.operationId(), id, entry.revision, entry.paused, Instant.now(),
                    "后续触发门禁已更新；已开始的任务不因此撤销，来源暂停仍独立生效。");
            operations.put(change.operationId(), new Operation(id, change, receipt));
            return receipt;
        } finally {
            entry.lock.unlock();
        }
    }

    /**
     * @param timer 固定计时器
     * @param operation 原编号
     * @return 本进程窗口内原回执
     * @throws NoSuchElementException 当编号、目标不匹配或回执过期时
     */
    public synchronized Receipt operation(UUID timer, UUID operation) {
        var found = operations.get(operation);
        if (found == null || !found.timer().equals(timer)
                || found.receipt().at().isBefore(Instant.now().minusSeconds(86400))) {
            throw new NoSuchElementException("定时器回执不存在、已过期或进程已重启。");
        }
        return found.receipt();
    }

    /**
     * 服务端借用的计时器与短门禁，不直接序列化。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     * <br>2026/11/04：注册时冻结展示合同，管理查询不修改业务对象。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.1-l
     */
    private static final class Entry {
        final UUID id = UUID.randomUUID();
        final ITimer<?> timer;
        final TimerPresentation presentation;
        final ReentrantLock lock = new ReentrantLock();
        volatile boolean paused;
        volatile long revision = 1;

        /** @param timer 原计时器 */
        Entry(ITimer<?> timer) {
            this.timer = timer;
            // 2026/11/04：分类与控制意图在实际注册时确认。
            this.presentation = timer.getTimerTaskConfig().getPresentation();
        }
    }

    /**
     * 明确的本进程动作。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param operationId 原编号
     * @param revision 原版本
     * @param paused 是否暂停后续触发
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public record Change(UUID operationId, long revision, boolean paused) {
        /**
         * @param operationId 原编号
         * @param revision 原版本
         * @param paused 明确门禁值
         */
        public Change {
            Objects.requireNonNull(operationId);
            if (revision < 1 || revision == Long.MAX_VALUE) {
                throw new IllegalArgumentException("定时器版本无效。");
            }
        }
    }

    /**
     * 实际动作事实，只在当前进程保留。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param operationId 原编号
     * @param timer 原计时器
     * @param revision 提交后版本
     * @param paused 当前门禁值
     * @param at 原提交时间
     * @param note 行为边界
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public record Receipt(UUID operationId, UUID timer, long revision, boolean paused, Instant at, String note) {}

    /**
     * 内部请求绑定，不导出计时器引用。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param timer 固定目标
     * @param change 原参数
     * @param receipt 原结果
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    private record Operation(UUID timer, Change change, Receipt receipt) {}

    /**
     * 可展示规则与真实支持的动作。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     * <br>2026/11/04：携带显式分类和实际调度来源，外部来源始终只读。
     *
     * @param id 本进程标识
     * @param name 安全任务名
     * @param state 观测状态
     * @param revision 门禁版本
     * @param rule 原计时器描述
     * @param actions 当前支持动作
     * @param category 稳定分类键
     * @param categoryName 中文分类名
     * @param description 用途说明
     * @param source 调度来源
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.1-l
     */
    public record View(UUID id, String name, String state, long revision, TimerObservation rule, List<String> actions,
                       String category, String categoryName, String description, String source) {
        /**
         * 2026/11/04：保留旧观测构造，未声明分类的任务不改变原控制意图。
         * @param id 原任务
         * @param name 名称
         * @param state 状态
         * @param revision 门禁版本
         * @param rule 原规则
         * @param actions 原支持动作
         */
        public View(UUID id, String name, String state, long revision, TimerObservation rule, List<String> actions) {
            this(id, name, state, revision, rule, actions, "unclassified", "未分类", "", "core");
        }
    }

    /**
     * 有界的当前注册目录。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     * <br>2026/11/04：分类列表来自分页前观测，不随当前筛选丢失。
     *
     * @param items 本页规则
     * @param more 是否还有后页
     * @param next 下一页游标
     * @param limited 是否达到目录观测容量
     * @param categories 当前观测窗口的可选分类
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.1-l
     */
    public record Page(List<View> items, boolean more, String next, boolean limited, List<Category> categories) {
        /**
         * 2026/11/04：保留旧分页对象构造，旧来源不伪造分类目录。
         * @param items 原窗口
         * @param more 是否还有后页
         * @param next 游标
         * @param limited 是否截断
         */
        public Page(List<View> items, boolean more, String next, boolean limited) {
            this(items, more, next, limited, List.of());
        }
    }

    /**
     * 当前目录中的分类，展示说明不包含业务执行引用。
     * @param id 稳定分类键
     * @param name 中文分类名
     * @author zExNocs
     * @date 2026/11/04
     * @since 4.7.1-l
     * @version 4.7.1-l
     */
    public record Category(String id, String name) {}
}
