package org.zexnocs.teanekoagent.memory.fact;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * 按稳定记录 ID 扫描的有界页面，包含过期证据，不冒充相关性检索结果。
 * @param records 不可变原记录
 * @param nextCursor 存在后续页时的最后已返回 ID；扫描结束为空
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record MemoryFactPage(List<MemoryFactRecord> records, @Nullable UUID nextCursor) {
    /**
     * 冻结页面，游标只能指向本页最后一条记录。
     * @param records 当前页
     * @param nextCursor 可选后继游标
     * @throws NullPointerException 当记录列表或元素为空时
     * @throws IllegalArgumentException 当游标与页面不一致时
     */
    public MemoryFactPage {
        records = List.copyOf(records);
        if (nextCursor != null && (records.isEmpty() || !records.getLast().id().equals(nextCursor))) { throw new IllegalArgumentException("事实分页游标与记录不一致。"); }
    }
}
