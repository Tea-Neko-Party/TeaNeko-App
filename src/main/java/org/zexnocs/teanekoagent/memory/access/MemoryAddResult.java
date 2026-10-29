package org.zexnocs.teanekoagent.memory.access;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.memory.fact.MemoryFactReceipt;

import java.util.Objects;
import java.util.UUID;

/**
 * 普通新增的最小提交回执，不返回重复旧记录中的管理审计原文。
 * @param status 原追加结果
 * @param recordId 已接受事实的稳定标识，冲突时为空
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
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
