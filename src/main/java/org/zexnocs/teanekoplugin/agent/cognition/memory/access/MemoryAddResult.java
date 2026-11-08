package org.zexnocs.teanekoplugin.agent.cognition.memory.access;

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoplugin.agent.cognition.memory.fact.MemoryFactReceipt;

import java.util.Objects;
import java.util.UUID;

/**
 * 普通新增的最小提交回执，不返回重复旧记录中的管理审计原文。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
 *
 * @param status 原追加结果
 * @param recordId 已接受事实的稳定标识，冲突时为空
 *
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
public record MemoryAddResult(MemoryFactReceipt.Status status, @Nullable UUID recordId) {
    /**
     * 保持成功与记录标识一致，不能用无记录回执声称写入成功。
     * @param status 提交结果
     * @param recordId 可选记录标识
     * @throws NullPointerException 当状态为空时
     * @throws IllegalArgumentException 当状态与标识不一致时
     */
    public MemoryAddResult {
        Objects.requireNonNull(status, "明确新增结果不能为空。");
        if ((status == MemoryFactReceipt.Status.CONFLICT) != (recordId == null)) { throw new IllegalArgumentException("明确新增结果与记录标识不一致。"); }
    }
}
