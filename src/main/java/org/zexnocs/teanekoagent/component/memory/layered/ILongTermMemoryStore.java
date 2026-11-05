package org.zexnocs.teanekoagent.component.memory.layered;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.component.memory.access.MemoryAddResult;
import org.zexnocs.teanekoagent.component.memory.fact.MemoryFactAppend;
import org.zexnocs.teanekoagent.component.memory.namespace.MemoryNamespaceReference;
import org.zexnocs.teanekoagent.component.personality.StyleObservations;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * 长期整理与召回事务端口，权威记录和索引同事务更新，不直接访问仓库。
 * <br>2026/11/03：联合提交风格观察，未支持的替换存储明确拒绝观察写入。
 * <br>2026/11/03：明确保存与后台整理共用长期记录及索引。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 * <br>2026/11/03：提供独立管理主体的纠正、遗忘与原回执查询，不借用记忆主体身份。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：提供安全失败分类、持久暂停状态及版本化管理恢复合同。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.1-l
 * @version 4.8.3-l
 */
public interface ILongTermMemoryStore {
    /**
     * 2026/11/03：显式管理入口保留独立来源和事务内权限。
     * @param reference 固定空间代次
     * @param actor 独立管理主体
     * @param id 记录
     * @param revision 原版本
     * @param text 修正正文，空值为遗忘
     * @param operation 原操作编号
     * @param now 固定时刻
     * @param guard 事务内授权复核
     * @return 同事务保存的公开结果
     * @throws UnsupportedOperationException 当自定义存储未支持管理时
     */
    default CompletionStage<LongMemories.Item> manage(MemoryNamespaceReference reference,
            org.zexnocs.teanekoagent.runtime.AgentActorContext actor, UUID id, long revision, @Nullable String text,
            UUID operation, Instant now, Runnable guard) {
        throw new UnsupportedOperationException("当前长期存储未实现独立管理。");
    }

    /**
     * 2026/11/03：回执查询不执行修改。
     * @param reference 原空间代次
     * @param actor 原管理主体及目标
     * @param operation 原操作
     * @param guard 当前授权
     * @return 原回执，不重复修改
     * @throws UnsupportedOperationException 当自定义存储未支持查询时
     */
    default CompletionStage<LongMemories.Item> managementOperation(MemoryNamespaceReference reference,
            org.zexnocs.teanekoagent.runtime.AgentActorContext actor, UUID operation, Runnable guard) {
        throw new UnsupportedOperationException("当前长期存储未实现管理回执查询。");
    }
    /**
     * 2026/11/03：保存已经过可信原文意图验证的本人事实，不调用模型。
     * @param command 固定身份、空间、原文及操作 ID
     * @param now 实际提交时刻
     * @return 实际事务回执
     */
    default CompletionStage<MemoryAddResult> appendExplicit(MemoryFactAppend command, Instant now) {
        return java.util.concurrent.CompletableFuture.failedStage(new UnsupportedOperationException("当前长期存储不支持明确保存。"));
    }
    /**
     * 领取尚未整理的中期片段，只有原任务租约拥有提交权。
     * @param stream 来源分区
     * @param now 当前时间
     * @return 有界整理输入
     */
    CompletionStage<Optional<LongMemories.Batch>> claim(MemoryLayers.Stream stream, Instant now);
    /**
     * 校验原话证据并原子保存长期记录、索引和整理进度。
     * @param batch 原租约输入
     * @param candidates 模型候选，空列表表示本批无需长期保存
     * @param now 当前时间
     * @return 原任务成功提交时为真
     */
    CompletionStage<Boolean> commit(LongMemories.Batch batch, List<LongMemories.Candidate> candidates, Instant now);
    /**
     * 2026/11/03：观察和原整理水位同事务保存，不以事后异步通知代替持久化。
     * @param batch 原租约
     * @param analysis 已生成的两类候选
     * @param target 宿主固定人格目标
     * @param now 当前时间
     * @param guard 事务内同步学习守卫
     * @return 原子提交结果
     */
    default CompletionStage<Boolean> commit(LongMemories.Batch batch, StyleObservations.Analysis analysis,
            StyleObservations.@Nullable Target target, Instant now, Runnable guard) {
        if (!analysis.styles().isEmpty()) { return java.util.concurrent.CompletableFuture.failedStage(new UnsupportedOperationException("当前长期存储不支持联合表达观察。")); }
        return commit(batch, analysis.memories(), now);
    }
    /**
     * 释放原租约并保存有界退避，不推进水位。
     * @param batch 原任务
     * @param now 失败观察时间
     * @return 退避提交阶段
     */
    CompletionStage<Void> retry(LongMemories.Batch batch, Instant now);
    /**
     * 2026/11/05：传入真实原因，生产存储限制同输入的重复无效生成。
     * @param batch 原任务
     * @param now 失败时刻
     * @param failure 实际异常，不得原样序列化
     * @return 退避或暂停已持久化的阶段
     */
    default CompletionStage<Void> retry(LongMemories.Batch batch, Instant now, @Nullable Throwable failure) {
        return retry(batch, now);
    }

    /**
     * 2026/11/05：查看当前分区后台状态，不领取任务或调用模型。
     * @param reference 固定分区引用
     * @param now 观察时刻
     * @param guard 事务内管理权限
     * @return 不含原话的状态
     * @throws UnsupportedOperationException 当自定义存储未实现此管理能力时
     */
    default CompletionStage<LongMemoryJobs.Status> consolidationState(MemoryNamespaceReference reference,
            Instant now, Runnable guard) {
        throw new UnsupportedOperationException("当前长期存储未提供整理状态管理。");
    }

    /**
     * 2026/11/05：只解除指定版本的暂停，不推进水位、不直接生成；原操作幂等。
     * @param reference 原空间与分区
     * @param actor 独立管理主体
     * @param revision 读取时的作业版本
     * @param operationId 原操作标识
     * @param now 当前时间
     * @param guard 事务内授权复核
     * @return 同事务保存的恢复回执
     * @throws UnsupportedOperationException 当自定义存储未实现恢复时
     */
    default CompletionStage<LongMemoryJobs.Status> resumeConsolidation(MemoryNamespaceReference reference,
            org.zexnocs.teanekoagent.runtime.AgentActorContext actor, long revision, UUID operationId,
            Instant now, Runnable guard) {
        throw new UnsupportedOperationException("当前长期存储未提供整理恢复。");
    }

    /**
     * 2026/11/05：查询原恢复回执，不再次解除暂停。
     * @param reference 原空间引用
     * @param actor 原管理主体
     * @param operationId 原操作
     * @param guard 当前权限
     * @return 原提交的状态快照
     * @throws UnsupportedOperationException 当自定义存储未提供回执时
     */
    default CompletionStage<LongMemoryJobs.Status> consolidationOperation(MemoryNamespaceReference reference,
            org.zexnocs.teanekoagent.runtime.AgentActorContext actor, UUID operationId, Runnable guard) {
        throw new UnsupportedOperationException("当前长期存储未提供整理恢复回执。");
    }
    /**
     * 按用户或话题索引召回共享空间，不按来源 scope 隐式过滤。
     * @param reference 原授权空间
     * @param subjectId 可选主体
     * @param query 可选话题
     * @param limit 一至二十条结果上限
     * @return 不含原话的有限结果
     */
    CompletionStage<LongMemories.Result> query(MemoryNamespaceReference reference, @Nullable UUID subjectId, String query, int limit);
    /**
     * 使用前检查已采用条目仍为同一有效版本，不在原资料中混入后来的替代值。
     * @param reference 固定空间引用
     * @param items 实际采用的有限投影
     * @return 所有条目仍可使用时为真
     */
    CompletionStage<Boolean> verify(MemoryNamespaceReference reference, List<LongMemories.Item> items);
    /**
     * 按原版本修改本人记忆，遗忘不删除原始聊天。
     * @param reference 当前空间
     * @param actorId 真实操作者 UUID
     * @param id 记录 UUID
     * @param revision 预期记录版本
     * @param text 新正文，空值表示遗忘
     * @param operationId 宿主固定操作 ID，重复调用返回原回执
     * @param sourceId 原用户消息来源标识
     * @param now 提交时间
     * @return 提交后的公开记录
     */
    CompletionStage<LongMemories.Item> change(MemoryNamespaceReference reference, UUID actorId, UUID id, long revision,
            @Nullable String text, UUID operationId, String sourceId, Instant now);
}
