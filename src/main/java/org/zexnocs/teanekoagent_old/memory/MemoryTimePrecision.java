package org.zexnocs.teanekoagent_old.memory;

/**
 * 记忆事件时间精度。

 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
public enum MemoryTimePrecision {
    /** 精确到秒或更高精度。 */
    EXACT,
    /** 精确到分钟。 */
    MINUTE,
    /** 精确到小时。 */
    HOUR,
    /** 精确到日期。 */
    DAY,
    /** 精确到周。 */
    WEEK,
    /** 精确到月份。 */
    MONTH,
    /** 精确到年份。 */
    YEAR,
    /** 只能确定大致时间范围。 */
    APPROXIMATE,
    /** 未知事件时间。 */
    UNKNOWN
}
