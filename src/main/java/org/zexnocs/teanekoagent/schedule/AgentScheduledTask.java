package org.zexnocs.teanekoagent.schedule;

import org.zexnocs.teanekoagent.agent.AgentActorContext;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 可持久化的任务快照；身份、目标及首次时间固定，revision 隔离旧回调。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @param id 幂等创建请求标识
 * @param spec 原始任务规格
 * @param nextFire 下一次计划时刻
 * @param status 当前状态
 * @param revision 状态修订号
 * @param lastOutcome 最近结果，不保存执行器异常原文
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.5.0-l
 * @version 4.5.0-l
 */
public record AgentScheduledTask(UUID id, Spec spec, Instant nextFire, Status status,
                                 long revision, String lastOutcome) {
    /**
     * 验证持久化快照完整性。
     * @param id 任务标识
     * @param spec 原规格
     * @param nextFire 计划时间
     * @param status 状态
     * @param revision 修订号
     * @param lastOutcome 安全结果
     */
    public AgentScheduledTask {
        Objects.requireNonNull(id); Objects.requireNonNull(spec); Objects.requireNonNull(nextFire);
        Objects.requireNonNull(status); Objects.requireNonNull(lastOutcome);
        if (revision < 1 || lastOutcome.length() > 256) { throw new IllegalArgumentException("任务修订号或结果长度不合法。"); }
    }

    /**
     * 固定秒数间隔不等于本地日历每日；日历规则留给后续独立策略。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param title 标题
     * @param creator 可信原生创建者及目标，不从模型参数构建
     * @param action 已注册动作名
     * @param arguments 有界业务参数
     * @param firstFire 明确的首次绝对时间
     * @param zoneId 明确的展示时区
     * @param intervalSeconds 零表示单次，否则至少六十秒
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.5.0-l
     * @version 4.5.0-l
     */
    public record Spec(String title, AgentActorContext creator, String action, Map<String, String> arguments,
                       Instant firstFire, String zoneId, long intervalSeconds) {
        /**
         * 拒绝系统伪造创建者、无时区及无界参数；不猜测含糊的自然语言时间。
         * 时区解析仅验证合法性，持久化仍保留调用方提供的原始标识。
         * @param title 标题
         * @param creator 创建者
         * @param action 动作名
         * @param arguments 动作参数
         * @param firstFire 首次时刻
         * @param zoneId 时区
         * @param intervalSeconds 重复间隔
         * @throws java.time.DateTimeException 当时区标识无法解析时
         * @throws NullPointerException 当创建者、首次时刻、时区或参数映射及其元素为空时
         * @throws IllegalArgumentException 当标题、来源、动作、参数或重复间隔无效时
         */
        @SuppressWarnings("ResultOfMethodCallIgnored")
        public Spec {
            Objects.requireNonNull(creator); Objects.requireNonNull(firstFire);
            ZoneId.of(zoneId);
            arguments = Map.copyOf(arguments);
            if (title == null || title.isBlank() || title.length() > 128
                    || creator.origin() != AgentActorContext.Origin.USER
                    || action == null || !action.matches("[a-z][a-z0-9_]{0,47}")
                    || arguments.size() > 16 || arguments.entrySet().stream().anyMatch(e ->
                    e.getKey().isBlank() || e.getKey().length() > 64 || e.getValue().length() > 4096)
                    || intervalSeconds != 0 && (intervalSeconds < 60 || intervalSeconds > 31_536_000)) {
                throw new IllegalArgumentException("任务规格不合法或超过限制。");
            }
        }
    }

    /**
     * 不确定结果停止调度，避免重启或重试再次产生副作用。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @author zExNocs
     * @date 2026/11/01
     * @since 4.5.0-l
     * @version 4.5.0-l
     */
    public enum Status { ACTIVE, RUNNING, COMPLETED, CANCELLED, EXPIRED, PAUSED, INDETERMINATE }
}
