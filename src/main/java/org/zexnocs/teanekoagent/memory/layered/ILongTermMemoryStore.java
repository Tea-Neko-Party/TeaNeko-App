package org.zexnocs.teanekoagent.memory.layered;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.memory.access.MemoryAddResult;
import org.zexnocs.teanekoagent.memory.fact.MemoryFactAppend;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceReference;
import org.zexnocs.teanekoagent.personality.StyleObservations;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * 长期整理与召回事务端口，权威记录和索引同事务更新，不直接访问仓库。
 * <br>2026/11/03：联合提交风格观察，未支持的替换存储明确拒绝观察写入。
 * <br>2026/11/03：明确保存与后台整理共用长期记录及索引。
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.0.0
 * @version 4.6.1-l
 */
public interface ILongTermMemoryStore {
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
