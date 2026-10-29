package org.zexnocs.teanekoagent.memory.recall;

import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceId;
import java.util.concurrent.CompletionStage;

/**
 * 有界稳定候选读取端口；在一次存储边界仲裁 generation，不能拼接不一致分页后宣称完整。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
@FunctionalInterface
public interface IMemoryRecallStore {
    /**
     * 按稳定 ID 扫描至多指定数量，显式报告是否仍有未检查记录。
     * @param namespaceId 当前授权空间
     * @param generation 固定数据代次
     * @param maxScanned 一至一万的检查上限
     * @return 去除审计原文的候选；缺失、旧代次或基础设施失败须异常结束
     */
    CompletionStage<MemoryRecallScan> scan(MemoryNamespaceId namespaceId, long generation, int maxScanned);
}
