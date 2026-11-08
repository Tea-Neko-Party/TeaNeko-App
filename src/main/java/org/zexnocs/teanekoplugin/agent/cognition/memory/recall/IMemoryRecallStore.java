package org.zexnocs.teanekoplugin.agent.cognition.memory.recall;

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoplugin.agent.cognition.memory.namespace.MemoryNamespaceId;
import java.util.concurrent.CompletionStage;

/**
 * 有界稳定候选读取端口；在一次存储边界仲裁 generation，不能拼接不一致分页后宣称完整。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
 *
 *
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.5.0-l
 * @version 4.9.0-l
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
