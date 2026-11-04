package org.zexnocs.teanekoagent.component.memory.fact;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * 按稳定记录 ID 扫描的有界页面，包含过期证据，不冒充相关性检索结果。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @param records 不可变原记录
 * @param nextCursor 存在后续页时的最后已返回 ID；扫描结束为空
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.5.0-l
 * @version 4.8.0-l
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
