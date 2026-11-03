package org.zexnocs.teanekocore.actuator.runtime;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITask;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * 原任务回执与日志的有界进程窗口，只存安全投影，不保存任务、结果、异常或执行控制。
 * 日志写入不等待查询锁；观测故障不会改变原业务结果。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
@Component
public final class RuntimeMonitor {
    private final UUID instance = UUID.randomUUID();
    private final Instant startedAt;
    private final Clock clock;
    private final Supplier<RuntimeSettings.Policy> settings;
    private final LinkedHashMap<UUID, Run> runs = new LinkedHashMap<>();
    private final ArrayDeque<Log> logs = new ArrayDeque<>();
    private final ReentrantLock logLock = new ReentrantLock();
    private final AtomicLong droppedLogs = new AtomicLong();
    private long runSequence;
    private long logSequence;
    private long droppedRuns;

    /**
     * @param files 原本地配置入口
     */
    @Autowired
    public RuntimeMonitor(IFileConfigService files) {
        this(Clock.systemUTC(), () -> files.requireCurrent(RuntimeSettings.class).snapshot());
    }

    /**
     * @param clock 显式时钟
     * @param settings 本机策略来源，不进行文件或网络操作
     */
    public RuntimeMonitor(Clock clock, Supplier<RuntimeSettings.Policy> settings) {
        this.clock = clock;
        this.settings = settings;
        startedAt = clock.instant();
    }

    /**
     * @return 当前策略；配置尚未加载或损坏时回到有界且不采集正文的安全值
     */
    public RuntimeSettings.Policy policy() {
        try {
            return settings.get();
        } catch (RuntimeException failure) {
            return new RuntimeSettings().snapshot();
        }
    }

    /**
     * @param task 已由原服务注册的真实任务，只在完成回调中借用
     */
    public void observe(ITask<?> task) {
        try {
            begin(task.getKey(), task.getConfig().getName());
            task.getFuture().asStage().whenComplete((result, failure) -> {
                String state = failure == null && result != null && result.isSuccess() ? "SUCCESS" : "FAILED";
                finish(task.getKey(), state);
            });
        } catch (RuntimeException ignored) {
            // 观测不可改变原任务的注册与完成路径。
        }
    }

    /**
     * @param id 原任务 UUID
     * @param name 原业务任务名，不包含结果正文
     */
    public synchronized void begin(UUID id, String name) {
        trimRuns();
        runs.put(id, new Run(++runSequence, id, safe(name, 128), "QUEUED", "等待执行", clock.instant(), null, null));
        trimRuns();
    }

    /**
     * @param id 原任务 UUID
     * @param state QUEUED、RUNNING 或 WAITING_RESULT
     * @param stage 当前同步阶段，不改变原单线程游标
     */
    public synchronized void phase(UUID id, String state, String stage) {
        var old = runs.get(id);
        if (old != null && old.finishedAt() == null) {
            runs.put(id, new Run(old.sequence(), id, old.name(), state, safe(stage, 128), old.createdAt(),
                    old.startedAt() == null && state.equals("RUNNING") ? clock.instant() : old.startedAt(), null));
        }
    }

    /**
     * @param id 原任务 UUID
     * @param state 原 Core 回执结果，不声明外层异步业务已经结束
     */
    public synchronized void finish(UUID id, String state) {
        var old = runs.get(id);
        if (old != null) {
            runs.put(id, new Run(old.sequence(), id, old.name(), state, "原任务回执已结束", old.createdAt(), old.startedAt(), clock.instant()));
        }
        trimRuns();
    }

    /** 清理仅限内存窗口，不取消任何原任务。 */
    private void trimRuns() {
        var policy = policy();
        Instant cutoff = clock.instant().minusSeconds(policy.retentionMinutes() * 60L);
        var iterator = runs.values().iterator();
        while (iterator.hasNext()) {
            var run = iterator.next();
            if (run.createdAt().isBefore(cutoff) || runs.size() > policy.maxRuns()) {
                iterator.remove();
                droppedRuns++;
            }
        }
    }

    /**
     * @param original 上次实例标识，空表示首次查询
     * @param before 更早页游标，零表示最新页
     * @param limit 每页一至一百项
     * @param state 精确状态或空
     * @param search 任务名称过滤
     * @return 当前进程窗口，窗口重置和丢弃数量明确报告
     */
    public synchronized RunPage runs(String original, long before, int limit, String state, String search) {
        validate(limit, state, search);
        trimRuns();
        boolean reset = !original.isEmpty() && !original.equals(instance.toString());
        long cursor = reset ? 0 : before;
        var matched = runs.values().stream().filter(run -> cursor == 0 || run.sequence() < cursor)
                .filter(run -> state.isEmpty() || run.state().equals(state))
                .filter(run -> run.name().toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT)))
                .sorted(Comparator.comparingLong(Run::sequence).reversed()).limit(limit + 1L).toList();
        var items = matched.stream().limit(limit).toList();
        return new RunPage(instance, startedAt, items, items.isEmpty() ? 0 : items.getLast().sequence(), reset,
                matched.size() > limit, droppedRuns);
    }

    /**
     * @param id 当前进程实际任务
     * @return 仅安全投影，过期或缺失明确报告
     */
    public synchronized Run detail(UUID id) {
        trimRuns();
        return Optional.ofNullable(runs.get(id)).orElseThrow(() -> new NoSuchElementException("任务不在当前观测窗口。"));
    }

    /**
     * @param level 实际日志等级
     * @param namespace 实际日志来源
     * @param message 原格式化正文，仅本机开启后才进行有界脱敏
     * @param requestId 结构化请求号，不从原始正文猜测
     * @param scope 结构化区域标识
     * @param plugin 结构化插件标识
     */
    public void log(String level, String namespace, String message, String requestId, String scope, String plugin) {
        if (!logLock.tryLock()) {
            droppedLogs.incrementAndGet();
            return;
        }
        try {
            var policy = policy();
            trimLogs(policy);
            logs.addLast(new Log(++logSequence, clock.instant(), safe(level, 8), safe(namespace, 180),
                    policy.captureMessages() ? safe(message, 2048) : "正文采集未开启", identifier(requestId, 64), identifier(scope, 256), identifier(plugin, 64)));
            trimLogs(policy);
        } catch (RuntimeException ignored) {
            droppedLogs.incrementAndGet();
        } finally {
            logLock.unlock();
        }
    }

    /**
     * @param policy 当前有界策略
     */
    private void trimLogs(RuntimeSettings.Policy policy) {
        Instant cutoff = clock.instant().minusSeconds(policy.retentionMinutes() * 60L);
        while (!logs.isEmpty() && (logs.size() > policy.maxLogs() || logs.getFirst().at().isBefore(cutoff))) {
            logs.removeFirst();
            droppedLogs.incrementAndGet();
        }
    }

    /**
     * @param query 已验证的过滤参数
     * @return 扫描游标而非匹配数量游标，过滤不能造成重复或跳页
     */
    public LogPage logs(LogQuery query) {
        validate(query.limit(), query.namespace(), query.text());
        logLock.lock();
        try {
            var policy = policy();
            trimLogs(policy);
            boolean reset = !query.instance().isEmpty() && !query.instance().equals(instance.toString());
            long after = reset ? 0 : query.after();
            long oldest = logs.isEmpty() ? logSequence + 1 : logs.getFirst().sequence();
            var result = new ArrayList<Log>();
            long next = after;
            boolean more = false;
            for (var log : logs) {
                if (log.sequence() <= after) {
                    continue;
                }
                if (result.size() == query.limit()) {
                    more = true;
                    break;
                }
                next = log.sequence();
                if ((query.level().isEmpty() || query.level().equals(log.level()))
                        && log.namespace().contains(query.namespace()) && log.message().contains(query.text())
                        && (query.requestId().isEmpty() || log.requestId().equals(query.requestId()))
                        && (query.scope().isEmpty() || log.scope().equals(query.scope()))
                        && (query.plugin().isEmpty() || log.plugin().equals(query.plugin()))
                        && (query.from() == null || !log.at().isBefore(query.from()))
                        && (query.through() == null || !log.at().isAfter(query.through()))) {
                    result.add(log);
                }
            }
            return new LogPage(instance, startedAt, List.copyOf(result), next, more, reset,
                    after != 0 && after < oldest - 1, droppedLogs.get(), policy);
        } finally {
            logLock.unlock();
        }
    }

    /**
     * @param limit 有界页大小
     * @param first 第一过滤项
     * @param second 第二过滤项
     */
    private static void validate(int limit, String first, String second) {
        if (limit < 1 || limit > 100 || first.length() > 256 || second.length() > 256) {
            throw new IllegalArgumentException("运行查询参数超过上限。");
        }
    }

    /**
     * 结构化标识不能使用正文的长令牌裁剪，否则 UUID 会失去可关联性。
     * @param value 来源明确提供的结构化标识
     * @param maximum 标识上限
     * @return 合法显示标识，非结构化文本丢弃
     */
    private static String identifier(String value, int maximum) {
        return value.length() <= maximum && value.matches("[-A-Za-z0-9_.:@/]*") ? value : "";
    }

    /**
     * 常见凭据标识、URL 及长令牌整段隐藏；不能识别任意业务自行输出的裸秘密。
     * @param value 原显示文本
     * @param maximum 字符上限
     * @return 有界、去控制字符的显示文本
     */
    public static String safe(String value, int maximum) {
        String text = value.substring(0, Math.min(value.length(), 8192));
        if (text.matches("(?is).*(?:api[-_ ]?key|authorization|bearer|password|client[-_ ]?secret|access[-_ ]?token|admin[-_ ]?token).*")) {
            return "包含认证字段的日志已隐藏";
        }
        text = text.replaceAll("https?://\\S+", "[地址已隐藏]")
                .replaceAll("[A-Za-z0-9_+/=-]{32,}", "[长标识已隐藏]")
                .replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", " ");
        return text.substring(0, Math.min(maximum, text.length()));
    }

    /**
     * 原 Core 任务的观测，不代表外层业务链。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param sequence 创建游标
     * @param id 原任务号
     * @param name 中文任务名
     * @param state 当前回执状态
     * @param stage 当前同步阶段
     * @param createdAt 注册时间
     * @param startedAt 实际开始时间
     * @param finishedAt 原回执结束时间
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public record Run(long sequence, UUID id, String name, String state, String stage, Instant createdAt,
                      @Nullable Instant startedAt, @Nullable Instant finishedAt) {}

    /**
     * 任务窗口与重置语义。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param instance 当前进程标识
     * @param startedAt 窗口起点
     * @param items 本页任务
     * @param next 更早页游标
     * @param reset 原实例已变化
     * @param more 是否还有匹配页
     * @param dropped 清理或容量淘汰总数
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public record RunPage(UUID instance, Instant startedAt, List<Run> items, long next, boolean reset, boolean more, long dropped) {}

    /**
     * 安全日志投影，不携带原异常、参数数组或 MDC 对象。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param sequence 本窗口序号
     * @param at 观察时间
     * @param level 日志等级
     * @param namespace 实际日志来源
     * @param message 有界显示正文
     * @param requestId 结构化请求号
     * @param scope 结构化范围
     * @param plugin 结构化插件
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public record Log(long sequence, Instant at, String level, String namespace, String message, String requestId, String scope, String plugin) {}

    /**
     * 日志查询只访问内存窗口。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param instance 上次实例标识
     * @param after 原扫描游标
     * @param limit 页大小
     * @param level 精确等级或空
     * @param namespace 来源包含文本
     * @param text 显示正文包含文本
     * @param requestId 精确请求号
     * @param scope 精确范围
     * @param plugin 精确插件
     * @param from 起始时间
     * @param through 结束时间
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public record LogQuery(String instance, long after, int limit, String level, String namespace, String text,
                           String requestId, String scope, String plugin, @Nullable Instant from, @Nullable Instant through) {
        /**
         * @param instance 原进程
         * @param after 原扫描游标
         * @param limit 页大小
         * @param level 等级
         * @param namespace 来源过滤
         * @param text 正文过滤
         * @param requestId 请求号
         * @param scope 范围
         * @param plugin 插件
         * @param from 起始时间
         * @param through 结束时间
         */
        public LogQuery {
            if (instance.length() > 64 || after < 0 || limit < 1 || limit > 100
                    || !Set.of("", "TRACE", "DEBUG", "INFO", "WARN", "ERROR").contains(level)
                    || Stream.of(namespace, text, requestId, scope, plugin).anyMatch(value -> value.length() > 256)
                    || from != null && through != null && from.isAfter(through)) {
                throw new IllegalArgumentException("日志过滤参数无效。");
            }
        }
    }

    /**
     * 日志扫描结果，不承诺进程外持久历史。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param instance 当前进程
     * @param startedAt 当前窗口起点
     * @param items 本页日志
     * @param next 扫描游标
     * @param more 是否还有未扫描条目
     * @param reset 是否已更换进程
     * @param gap 原游标已被淘汰
     * @param dropped 丢弃总量
     * @param policy 当前本地保留策略
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public record LogPage(UUID instance, Instant startedAt, List<Log> items, long next, boolean more, boolean reset,
                          boolean gap, long dropped, RuntimeSettings.Policy policy) {}
}
