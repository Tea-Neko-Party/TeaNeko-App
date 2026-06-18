package org.zexnocs.teanekoagent_old.memory;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekocore.framework.description.Description;

import java.time.Instant;

/**
 * 记忆所描述事件的时间点或时间范围。
 * <br>该对象不负责解析自然语言时间。Agent 应先把自然语言转换成 ISO-8601 时间，再写入该对象。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
@Getter
@Setter
@NoArgsConstructor
public class MemoryTimeRange {
    /**
     * 事件时间范围起点。
     */
    @Nullable
    @Description("事件时间范围起点，使用 ISO-8601 UTC 时间；未知时为 null。")
    private Instant start = null;

    /**
     * 事件时间范围终点。
     */
    @Nullable
    @Description("事件时间范围终点，使用 ISO-8601 UTC 时间；时间点事件可与 start 相同。")
    private Instant end = null;

    /**
     * 事件时间精度。
     */
    @Description("时间精度。重要记忆应尽量使用 EXACT、MINUTE 或 DAY；模糊时间可使用 WEEK、MONTH 或 APPROXIMATE。")
    private MemoryTimePrecision precision = MemoryTimePrecision.UNKNOWN;

    /**
     * 原始时间表达，例如“一周前左右”。
     */
    @Description("用户或来源中的原始时间表达，用于解释时间范围。")
    private String originalExpression = "";

    /**
     * 创建精确时间点。
     *
     * @param instant 时间点。
     * @return 精确时间范围。
     */
    public static MemoryTimeRange exact(Instant instant) {
        var range = new MemoryTimeRange();
        range.setStart(instant);
        range.setEnd(instant);
        range.setPrecision(instant == null ? MemoryTimePrecision.UNKNOWN : MemoryTimePrecision.EXACT);
        return range;
    }

    /**
     * 判断当前时间范围是否与查询范围相交。
     *
     * @param queryStart 查询起点；为空表示不限制。
     * @param queryEnd   查询终点；为空表示不限制。
     * @param fallback   事件时间未知时使用的记录时间。
     * @return 如果时间范围相交则返回 {@code true}。
     */
    public boolean overlaps(@Nullable Instant queryStart,
                            @Nullable Instant queryEnd,
                            @Nullable Instant fallback) {
        var effectiveStart = start == null ? fallback : start;
        var effectiveEnd = end == null ? effectiveStart : end;
        if (effectiveStart == null && effectiveEnd == null) {
            return queryStart == null && queryEnd == null;
        }
        if (effectiveStart == null) {
            effectiveStart = effectiveEnd;
        }
        if (effectiveEnd.isBefore(effectiveStart)) {
            var temporary = effectiveStart;
            effectiveStart = effectiveEnd;
            effectiveEnd = temporary;
        }
        return (queryStart == null || !effectiveEnd.isBefore(queryStart))
                && (queryEnd == null || !effectiveStart.isAfter(queryEnd));
    }

    /**
     * 判断当前对象是否包含有效事件时间。
     *
     * @return 是否包含时间点或范围。
     */
    public boolean isKnown() {
        return start != null || end != null;
    }
}
