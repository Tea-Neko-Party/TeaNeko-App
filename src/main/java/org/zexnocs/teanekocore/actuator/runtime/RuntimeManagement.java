package org.zexnocs.teanekocore.actuator.runtime;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekocore.actuator.timer.TimerDirectory;
import org.zexnocs.teanekocore.plugin.management.GlobalManagementResource;
import org.zexnocs.teanekocore.plugin.management.ManagementContext;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Core 运行与日志的实例级类型化管理贡献，所有操作继续经过原授权网关。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：统一规则目录增加分类、规则类型、状态及名称筛选。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.1-l
 */
public final class RuntimeManagement {
    /** 禁止实例化贡献命名空间。 */
    private RuntimeManagement() {}

    /**
     * 运行查询与实际支持的计时器门禁动作。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     * <br>2026/11/04：定时筛选与运行任务状态独立校验，旧构造入口保持空筛选。
     *
     * @param action TIMERS、RUNS、DETAIL、CHANGE 或 OPERATION
     * @param after 定时器游标，首页为空
     * @param before 任务更早页游标
     * @param limit 页大小
     * @param instance 原进程标识
     * @param state 精确规则或任务状态
     * @param search 规则或任务名称过滤
     * @param id 明确原任务或计时器标识
     * @param change 计时器明确变更
     * @param operationId 原回执编号
     * @param category 精确定时分类
     * @param kind 精确定时规则类型
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.1-l
     */
    public record Query(String action, String after, long before, int limit, String instance, String state,
                        String search, @Nullable UUID id, TimerDirectory.@Nullable Change change, @Nullable UUID operationId,
                        String category, String kind) {
        /**
         * 2026/11/04：保留既有客户端构造入口，缺省不筛选分类与规则类型。
         * @param action 动作
         * @param after 原规则游标
         * @param before 原任务游标
         * @param limit 页大小
         * @param instance 原进程
         * @param state 状态
         * @param search 名称
         * @param id 原目标
         * @param change 明确门禁动作
         * @param operationId 原操作编号
         */
        public Query(String action, @Nullable String after, long before, int limit, @Nullable String instance,
                @Nullable String state, @Nullable String search, @Nullable UUID id,
                TimerDirectory.@Nullable Change change, @Nullable UUID operationId) {
            this(action, after, before, limit, instance, state, search, id, change, operationId, "", "");
        }
        /**
         * @param action 明确动作
         * @param after 定时器原游标
         * @param before 原任务游标
         * @param limit 页大小
         * @param instance 原进程
         * @param state 状态过滤
         * @param search 名称过滤
         * @param id 固定目标
         * @param change 明确变更
         * @param operationId 原编号
         * @param category 定时分类键
         * @param kind 定时规则类型
         * @throws IllegalArgumentException 当动作、游标、页大小或必需目标无效时
         */
        public Query(String action, @Nullable String after, long before, int limit, @Nullable String instance,
                @Nullable String state, @Nullable String search, @Nullable UUID id,
                TimerDirectory.@Nullable Change change, @Nullable UUID operationId,
                @Nullable String category, @Nullable String kind) {
            Objects.requireNonNull(action);
            after = after == null ? "" : after;
            instance = instance == null ? "" : instance;
            state = state == null ? "" : state;
            search = search == null ? "" : search;
            // 2026/11/04：旧 HTTP 请求省略新筛选时仍按完整目录查询。
            category = category == null ? "" : category;
            kind = kind == null ? "" : kind;
            var states = action.equals("TIMERS")
                    ? Set.of("", "ACTIVE", "PAUSED", "SOURCE_PAUSED", "REMOVED", "UNKNOWN", "RUNNING")
                    : Set.of("", "QUEUED", "RUNNING", "WAITING_RESULT", "SUCCESS", "FAILED");
            if (!Set.of("TIMERS", "RUNS", "DETAIL", "CHANGE", "OPERATION").contains(action)
                    || limit < 1 || limit > 100 || before < 0 || after.length() > 36
                    || instance.length() > 64 || search.length() > 256
                    || !states.contains(state)
                    || !category.isEmpty() && !category.matches("[a-z][a-z0-9_.-]{0,63}")
                    || !kind.isEmpty() && !kind.matches("[A-Z][A-Z0-9_]{0,31}")
                    || Set.of("DETAIL", "CHANGE", "OPERATION").contains(action) && id == null
                    || action.equals("CHANGE") && change == null || action.equals("OPERATION") && operationId == null) {
                throw new IllegalArgumentException("运行管理请求无效。");
            }
            this.action = action;
            this.after = after;
            this.before = before;
            this.limit = limit;
            this.instance = instance;
            this.state = state;
            this.search = search;
            this.id = id;
            this.change = change;
            this.operationId = operationId;
            this.category = category;
            this.kind = kind;
        }
    }

    /**
     * 只访问原运行窗口和原计时器目录。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     * <br>2026/11/04：转发经过类型绑定的筛选，不在分页后过滤。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.1-l
     */
    @Component
    public static final class Runtime implements GlobalManagementResource<Query, Object> {
        private final RuntimeMonitor monitor;
        private final TimerDirectory timers;

        /**
         * @param monitor 原回执观测
         * @param timers 实际计时器目录
         */
        public Runtime(RuntimeMonitor monitor, TimerDirectory timers) {
            this.monitor = monitor;
            this.timers = timers;
        }

        /** @return 明确的实例级资源权限 */
        @Override
        public Descriptor descriptor() {
            return new Descriptor("teaneko.core", "runtime", 1, "runtime.manage", true, List.of());
        }

        /** @return 固定请求类型 */
        @Override
        public Class<Query> requestType() {
            return Query.class;
        }

        /**
         * @param context 当前宿主授权
         * @param query 明确查询或动作
         * @return 真实窗口或原操作回执
         */
        @Override
        public CompletionStage<Object> execute(ManagementContext context, Query query) {
            context.checkActive();
            if (!context.scopeRef().equals("global")) {
                throw new SecurityException("运行资源要求明确的实例级授权。");
            }
            Object result = switch (query.action()) {
                // 2026/11/04：复用原授权网关查询统一的只读规则目录。
                case "TIMERS" -> timers.page(query.after(), query.limit(), query.category(), query.kind(), query.state(), query.search());
                case "RUNS" -> monitor.runs(query.instance(), query.before(), query.limit(), query.state(), query.search());
                case "DETAIL" -> monitor.detail(Objects.requireNonNull(query.id()));
                case "CHANGE" -> timers.change(Objects.requireNonNull(query.id()), Objects.requireNonNull(query.change()), context::checkActive);
                case "OPERATION" -> timers.operation(Objects.requireNonNull(query.id()), Objects.requireNonNull(query.operationId()));
                default -> throw new IllegalArgumentException("运行管理动作无效。");
            };
            context.checkActive();
            return CompletableFuture.completedStage(result);
        }
    }

    /**
     * 实例级日志有界读取，不开放文件下载或插件对象。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    @Component
    public static final class Logs implements GlobalManagementResource<RuntimeMonitor.LogQuery, RuntimeMonitor.LogPage> {
        private final RuntimeMonitor monitor;

        /** @param monitor 原日志窗口 */
        public Logs(RuntimeMonitor monitor) {
            this.monitor = monitor;
        }

        /** @return 独立日志读取权限 */
        @Override
        public Descriptor descriptor() {
            return new Descriptor("teaneko.core", "logs", 1, "runtime.logs.inspect", false, List.of());
        }

        /** @return 固定过滤请求 */
        @Override
        public Class<RuntimeMonitor.LogQuery> requestType() {
            return RuntimeMonitor.LogQuery.class;
        }

        /**
         * @param context 当前宿主授权
         * @param query 有界过滤
         * @return 当前进程日志页
         */
        @Override
        public CompletionStage<RuntimeMonitor.LogPage> execute(ManagementContext context, RuntimeMonitor.LogQuery query) {
            context.checkActive();
            if (!context.scopeRef().equals("global")) {
                throw new SecurityException("日志资源要求明确的实例级授权。");
            }
            var result = monitor.logs(query);
            context.checkActive();
            return CompletableFuture.completedStage(result);
        }
    }
}
