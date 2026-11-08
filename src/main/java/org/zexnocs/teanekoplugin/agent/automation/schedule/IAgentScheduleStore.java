package org.zexnocs.teanekoplugin.agent.automation.schedule;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.UnaryOperator;

/**
 * EasyDataScheduleStore 的类型化业务端口；实现由所属插件提供，底层不依赖插件实现。
 * <br>2026/11/07：分离声明与实现，保留原真实完成和权限约束。
 *
 * <br>2026/11/07：业务归属与通用底座按职责分离，保留持久身份及完成边界。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
public interface IAgentScheduleStore {
    /**
     * 同一 ID 重放只允许完全相同的原始规格，不复活已取消任务。
     * @param task 新任务
     * @return 提交后的已有或新建快照
     */
    CompletionStage<AgentScheduledTask> create(AgentScheduledTask task);
    /**
     * 在行锁内执行状态比较与写入，事务重试重新读取原状态。
     * @param id 任务标识
     * @param update 纯状态转换，不进行外部副作用
     * @return 提交后的快照
     */
    CompletionStage<AgentScheduledTask> update(UUID id, UnaryOperator<AgentScheduledTask> update);
    /**
     * 同一发生只能由实际完成状态交换的调用方执行，不能把别人已认领的快照当作自己的回执。
     * @param expected 原活动快照
     * @param next 认领或暂停状态
     * @return 当前事务是否真正取得交换权
     */
    CompletionStage<Boolean> compareAndSet(AgentScheduledTask expected, AgentScheduledTask next);
    /**
     * 在事务内读取权威状态，不使用进程缓存判断执行权。
     * @param id 任务标识
     * @return 当前快照或空结果
     */
    CompletionStage<Optional<AgentScheduledTask>> find(UUID id);
    /**
     * 按稳定 ID 游标有界读取，调用方负责按可信 scope 过滤后展示。
     * @param after 排他游标，首页为空字符串
     * @return 最多一百项，不代表完整任务集合
     */
    CompletionStage<List<AgentScheduledTask>> page(String after);
    /**
     * 2026/11/05：运行时只扫描已到期的活动索引，历史终态和远期任务不占用发现容量。
     * @param now 当前绝对时间
     * @param limit 当前可用执行槽位，最多一百项
     * @return 经权威状态过滤的到期候选，执行权仍由后续版本交换决定
     */
    CompletionStage<List<AgentScheduledTask>> due(java.time.Instant now, int limit);
}
