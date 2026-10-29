package org.zexnocs.teanekoagent.memory.namespace;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * 记忆空间元数据的事务端口；调用者是可信业务服务，聊天权限由其上层边界检查。
 * 创建记录和个人配额索引必须在同一事务提交，不允许无保护的整列表覆盖。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public interface IMemoryNamespaceStore {
    /**
     * 从持久状态读取元数据，不自动登记默认值。
     * @param id 稳定空间标识
     * @return 事务完成后的当前记录或不存在
     */
    CompletionStage<Optional<MemoryNamespace>> find(MemoryNamespaceId id);

    /**
     * 登记初始空间并原子检查创建者配额；相同来源重试返回原记录，创建时刻不被覆盖。
     * @param candidate revision 与 generation 均为零的候选
     * @param maxCreated 每个创建者的活动自建空间上限，默认共享空间不占用
     * @return 提交完成后的创建、重复、冲突或配额回执
     * @throws IllegalArgumentException 当候选不是初始记录或配额不是正值时
     */
    CompletionStage<MemoryNamespaceMutation> create(MemoryNamespace candidate, int maxCreated);

    /**
     * 从创建者索引分页读取，不扫描整个数据表，也不将绑定者当作创建者。
     * @param creator TeaNeko 用户 UUID
     * @param offset 从零开始的偏移
     * @param limit 一至一百的页面大小
     * @return 已提交记录组成的不可变页面
     * @throws IllegalArgumentException 当分页参数超出范围时
     */
    CompletionStage<List<MemoryNamespace>> listCreated(UUID creator, int offset, int limit);

    /**
     * 原子推进数据代次，为后续经过 DEBUG 授权的逻辑清空提供存储原语，不物理删除事实。
     * 相同请求与原预期版本重复提交只确认一次；旧版本在后续变更后返回冲突，不再次推进。
     * @param id 目标空间
     * @param expectedRevision 调用方读取的原始版本，重试必须保留
     * @param requestId 宿主固定的管理请求 ID
     * @return 持久化后的换代、重复确认、冲突或不存在
     * @throws IllegalArgumentException 当预期版本为负时
     */
    CompletionStage<MemoryNamespaceMutation> advanceGeneration(MemoryNamespaceId id, long expectedRevision, UUID requestId);
}
