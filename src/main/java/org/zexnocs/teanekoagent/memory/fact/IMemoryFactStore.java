package org.zexnocs.teanekoagent.memory.fact;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceId;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * 事实存储端口，事务内校验 generation；调用者另行验证启用、当前绑定、明确新增意图及权限。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public interface IMemoryFactStore {
    /**
     * 原子追加记录、重复内容索引和原操作回执，提交成功后才公开结果。
     * @param command 稳定动作及可信来源
     * @param now 本次初次尝试固定的可信记录时刻
     * @return 已存储、完全重复或同操作 ID 冲突，旧代次异常结束
     */
    CompletionStage<MemoryFactReceipt> append(MemoryFactAppend command, Instant now);
    /**
     * 事务内验证有效代次后读取原记录，旧代次不伪装为不存在。
     * @param namespaceId 空间
     * @param generation 预期代次
     * @param id 记录 ID
     * @return 本代次的原记录，空间缺失或代次变化时异常结束
     */
    CompletionStage<Optional<MemoryFactRecord>> find(MemoryNamespaceId namespaceId, long generation, UUID id);
    /**
     * 按空间和有效代次扫描记录，不因来源用户/scope/Agent 自动隔离共享数据。
     * @param namespaceId 空间
     * @param generation 预期代次
     * @param after 前页游标，首次为空
     * @param limit 一至一百的页面大小
     * @return 有界原证据页面，后续 Recall 另行过滤有效期和相关性
     */
    CompletionStage<MemoryFactPage> list(MemoryNamespaceId namespaceId, long generation, @Nullable UUID after, int limit);
}
