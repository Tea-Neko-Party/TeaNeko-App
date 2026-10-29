package org.zexnocs.teanekoagent.history;

import java.time.Instant;
import java.util.concurrent.CompletionStage;

/**
 * 历史存储端口，所有写入 Stage 均等待事务提交；不承担业务授权。
 * @author zExNocs
 * @date 2026/10/30
 * @since 4.0.0
 * @version 4.5.0-l
 */
public interface IHistoryStore {
    /**
     * 独立追加有界消息，不覆盖同来源的冲突正文。
     * @param context 已授权固定边界
     * @param message 真实消息
     * @param expiresAt 保留期终点
     * @return 提交后是否新增
     */
    CompletionStage<Boolean> append(HistoryContext context, HistoryMessage message, Instant expiresAt);
    /**
     * 按固定边界有界扫描，过滤过期与查询条件。
     * @param context 固定边界
     * @param query 筛选与预算
     * @param now 固定查询时刻
     * @return 页面及继续游标
     */
    CompletionStage<HistoryPage> search(HistoryContext context, HistoryQuery query, Instant now);
    /**
     * 清理固定数量的过期行，不全表加载。
     * @param now 当前时刻
     * @return 实际清理行数，单次最多一百
     */
    CompletionStage<Integer> prune(Instant now);
}
