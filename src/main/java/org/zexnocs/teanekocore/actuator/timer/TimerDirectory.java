package org.zexnocs.teanekocore.actuator.timer;

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
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
@Component
public final class TimerDirectory {
    private final Map<ITimer<?>, Entry> timers = new ConcurrentHashMap<>();
    private final Map<UUID, Entry> ids = new ConcurrentHashMap<>();
    private final LinkedHashMap<UUID, Operation> operations = new LinkedHashMap<>();

    /**
     * @param timer 原服务刚注册的计时器
     */
    public void register(ITimer<?> timer) {
        if (timers.size() >= 4096) {
            return;
        }
        timers.computeIfAbsent(timer, value -> {
            var entry = new Entry(value);
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
        if (limit < 1 || limit > 100 || after.length() > 36) {
            throw new IllegalArgumentException("定时器分页参数无效。");
        }
        var found = ids.values().stream().filter(entry -> entry.id.toString().compareTo(after) > 0)
                .sorted(Comparator.comparing(entry -> entry.id.toString())).limit(limit + 1L).toList();
        var items = found.stream().limit(limit).map(this::view).toList();
        return new Page(items, found.size() > limit, items.isEmpty() ? "" : items.getLast().id().toString(), timers.size() >= 4096);
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
            return new View(entry.id, RuntimeMonitor.safe(config.getTaskConfig().getName(), 128), state,
                    entry.revision, entry.timer.observation(), state.equals("REMOVED") ? List.of() : List.of(entry.paused ? "RESUME" : "PAUSE"));
        } catch (RuntimeException failure) {
            return new View(entry.id, "定时器状态暂不可用", "UNKNOWN", entry.revision,
                    new TimerObservation("CUSTOM", "规则暂不可用", null, false, ""), List.of());
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
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    private static final class Entry {
        final UUID id = UUID.randomUUID();
        final ITimer<?> timer;
        final ReentrantLock lock = new ReentrantLock();
        volatile boolean paused;
        volatile long revision = 1;

        /** @param timer 原计时器 */
        Entry(ITimer<?> timer) {
            this.timer = timer;
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
     *
     * @param id 本进程标识
     * @param name 安全任务名
     * @param state 观测状态
     * @param revision 门禁版本
     * @param rule 原计时器描述
     * @param actions 当前支持动作
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public record View(UUID id, String name, String state, long revision, TimerObservation rule, List<String> actions) {}

    /**
     * 有界的当前注册目录。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param items 本页规则
     * @param more 是否还有后页
     * @param next 下一页游标
     * @param limited 是否达到目录观测容量
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public record Page(List<View> items, boolean more, String next, boolean limited) {}
}
