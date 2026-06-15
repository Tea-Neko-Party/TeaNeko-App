package org.zexnocs.teanekoagent.memory;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;

/**
 * 记忆时间范围匹配测试。
 * <br>验证精确时间点、相交时间范围以及旧记忆记录的时间兼容逻辑。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
class MemoryTimeRangeTest {
    /**
     * 验证记忆时间范围能够匹配内部时间点和部分相交范围，并排除完全不相交的范围。
     */
    @Test
    void matchesExactPointAndOverlappingRange() {
        var range = new MemoryTimeRange();
        range.setStart(Instant.parse("2026-06-04T10:00:00Z"));
        range.setEnd(Instant.parse("2026-06-04T12:00:00Z"));
        range.setPrecision(MemoryTimePrecision.HOUR);

        Assertions.assertTrue(range.overlaps(
                Instant.parse("2026-06-04T11:00:00Z"),
                Instant.parse("2026-06-04T11:00:00Z"),
                null
        ));
        Assertions.assertTrue(range.overlaps(
                Instant.parse("2026-06-04T11:30:00Z"),
                Instant.parse("2026-06-04T13:00:00Z"),
                null
        ));
        Assertions.assertFalse(range.overlaps(
                Instant.parse("2026-06-05T00:00:00Z"),
                Instant.parse("2026-06-05T23:59:59Z"),
                null
        ));
    }

    /**
     * 验证没有事件时间的旧记忆记录会使用创建时间参与时间范围查询。
     */
    @Test
    void fallsBackToMemoryCreationTimeForLegacyRecord() {
        var record = new AgentMemoryRecord();
        record.setCreatedAt(Instant.parse("2026-06-04T10:00:00Z"));

        Assertions.assertTrue(record.occursWithin(
                Instant.parse("2026-06-04T00:00:00Z"),
                Instant.parse("2026-06-04T23:59:59Z")
        ));
    }
}
