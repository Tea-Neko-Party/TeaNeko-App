package org.zexnocs.teanekoagent.memory.admin;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.memory.fact.MemoryFactPage;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceId;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * DEBUG 记忆管理的持久化端口；事务内协调事实、索引、空间和操作回执，权限由宿主入口复核。
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.0.0
 * @version 4.5.0-l
 */
public interface IMemoryManagementStore {
    /**
     * 在一个事务中完成修改、快照克隆或逻辑清空及持久化回执。
     * @param command 固定原操作
     * @param now 初次提交时刻，数据库重试复用
     * @param guard 每次事务重试及提交前复核授权与取消的宿主检查，不持久化
     * @return 原子提交或明确拒绝
     */
    CompletionStage<MemoryManagementResult> apply(MemoryManagementCommand command, Instant now, Runnable guard);

    /**
     * 固定当前代次的有界证据导出，分页调用不能跨 clear 混合代次。
     * @param namespace 空间
     * @param generation 预期代次
     * @param after 前页游标
     * @param limit 一至一百的页大小
     * @return 保留原来源、版本和锁定信息的页面
     */
    CompletionStage<MemoryFactPage> page(MemoryNamespaceId namespace, long generation, @Nullable UUID after, int limit);

    /**
     * 有界回收一个已失效代次的事实和局部索引，不删除全局防重或其他业务域。
     * @param namespace 空间
     * @param obsoleteGeneration 必须小于当前代次
     * @param limit 一至一千的单批行数
     * @param guard 事务重试及提交前的可信授权检查
     * @return 实际删除行数，重复执行最终为零
     */
    CompletionStage<Integer> purge(MemoryNamespaceId namespace, long obsoleteGeneration, int limit, Runnable guard);
}
