package org.zexnocs.teanekoagent.history;

import org.jspecify.annotations.Nullable;
import java.time.Instant;

/**
 * 以持久行序号倒序分页，时间边界与字面关键词不接受任意查询表达式。
 * @param before 上页末行序号，首次使用 Long.MAX_VALUE
 * @param keyword 字面子串，空字符串表示不限
 * @param start 可选发生时间起点
 * @param end 可选发生时间终点
 * @param limit 返回条数，最多二十条
 * @param maxCharacters 完整结果消息数组的字符预算，最多六千
 * @author zExNocs
 * @date 2026/10/30
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record HistoryQuery(long before, String keyword, @Nullable Instant start, @Nullable Instant end, int limit, int maxCharacters) {
    /**
     * 拒绝无效游标和预算，时间均使用 Instant。
     * @param before 原游标
     * @param keyword 字面关键词
     * @param start 起点
     * @param end 终点
     * @param limit 条数
     * @param maxCharacters 字符预算
     * @throws IllegalArgumentException 当条件或容量非法时
     */
    public HistoryQuery {
        if (before <= 0 || keyword == null || keyword.length() > 128 || limit < 1 || limit > 20 || maxCharacters < 2 || maxCharacters > 6000
                || start != null && end != null && end.isBefore(start)) { throw new IllegalArgumentException("历史查询条件、游标或预算无效。"); }
    }
}
