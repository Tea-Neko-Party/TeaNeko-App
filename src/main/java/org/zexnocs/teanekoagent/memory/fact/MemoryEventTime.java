package org.zexnocs.teanekoagent.memory.fact;

import org.jspecify.annotations.Nullable;
import java.time.Instant;
import java.util.Objects;

/**
 * 事实的事件时间及原始表达，记录时间只作为显式允许的估算回退。
 * @param start 可选事件起点
 * @param end 可选事件终点，单侧端点表示时间点
 * @param precision 来源提供的精度
 * @param expression 原始时间表达
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record MemoryEventTime(@Nullable Instant start, @Nullable Instant end, Precision precision, String expression) {
    /**
     * 保留来源时间，不将倒置范围或伪造精度静默改写为精确事实。
     * @param start 可选起点
     * @param end 可选终点
     * @param precision 时间精度
     * @param expression 原始表达
     * @throws NullPointerException 当精度或表达为空时
     * @throws IllegalArgumentException 当范围倒置、表达过长或精度与端点不一致时
     */
    public MemoryEventTime {
        Objects.requireNonNull(precision, "记忆时间精度不能为空。"); Objects.requireNonNull(expression, "记忆时间表达不能为空。");
        if (start != null && end != null && end.isBefore(start) || expression.length() > 512
                || (start == null && end == null) != (precision == Precision.UNKNOWN)) {
            throw new IllegalArgumentException("记忆事件时间范围、精度或表达长度无效。");
        }
    }

    /**
     * 明确表示事件时间未知，不在构造时读取系统时钟。
     * @return 无事件端点的时间值
     */
    public static MemoryEventTime unknown() { return new MemoryEventTime(null, null, Precision.UNKNOWN, ""); }

    /**
     * 验证与查询范围的闭区间交集，未知事件可按显式策略回退记录时间。
     * @param queryStart 可选查询起点
     * @param queryEnd 可选查询终点
     * @param recordedAt 可信记录时间
     * @param includeEstimated 是否允许记录时间估算
     * @return 允许的时间依据与查询相交时为 true
     * @throws NullPointerException 当记录时间为空时
     * @throws IllegalArgumentException 当查询范围倒置时
     */
    public boolean overlaps(@Nullable Instant queryStart, @Nullable Instant queryEnd, Instant recordedAt, boolean includeEstimated) {
        Objects.requireNonNull(recordedAt, "记忆记录时间不能为空。");
        if (queryStart != null && queryEnd != null && queryEnd.isBefore(queryStart)) { throw new IllegalArgumentException("记忆查询时间范围不能倒置。"); }
        if (precision == Precision.UNKNOWN && !includeEstimated) { return false; }
        var from = start != null ? start : end != null ? end : recordedAt;
        var to = end != null ? end : from;
        return (queryStart == null || !to.isBefore(queryStart)) && (queryEnd == null || !from.isAfter(queryEnd));
    }

    /**
     * 事件时间精度，不以记录时间冒充已知事件时间。
     * @author zExNocs
     * @date 2026/10/29
     * @since 4.0.0
     * @version 4.5.0-l
     */
    public enum Precision { UNKNOWN, EXACT, MINUTE, HOUR, DAY, WEEK, MONTH, YEAR, APPROXIMATE }
}
