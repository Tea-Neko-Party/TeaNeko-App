package org.zexnocs.teanekoagent.component.memory.namespace;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.runtime.AgentActorContext;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 已登记记忆空间的不可变元数据；创建来源用于审计，不限制同空间事实的检索主体。
 * <br>2026/11/03：管理克隆记录独立管理主体，继续使用创建配额。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @param id 业务空间 ID
 * @param revision 元数据版本
 * @param generation 当前数据代次，逻辑清空先推进它
 * @param createdAt 首次提交的创建时刻
 * @param creation 用户或管理创建来源；默认共享空间为空且不占创建配额
 * @param lastGenerationRequest 最近一次换代请求，仅用于相同版本变更的重放确认
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.5.0-l
 * @version 4.8.0-l
 */
public record MemoryNamespace(MemoryNamespaceId id, long revision, long generation, Instant createdAt,
        @Nullable Creation creation, @Nullable UUID lastGenerationRequest) {
    /**
     * 固定元数据，拒绝把普通空间冒充免配额的共享空间。
     * @param id 稳定空间 ID
     * @param revision 元数据版本
     * @param generation 数据代次
     * @param createdAt 原始创建时刻
     * @param creation 可选人类创建来源
     * @param lastGenerationRequest 最近换代请求
     * @throws NullPointerException 当 ID 或创建时刻为空时
     * @throws IllegalArgumentException 当版本、创建来源或换代状态不一致时
     */
    public MemoryNamespace {
        Objects.requireNonNull(id, "记忆空间标识不能为空。");
        Objects.requireNonNull(createdAt, "记忆空间创建时刻不能为空。");
        if (generation < 0 || revision < generation
                || (creation == null) != id.equals(MemoryNamespaceId.SHARED)
                || creation != null && !id.equals(MemoryNamespaceId.created(creation.requestId()))
                || (generation == 0) != (lastGenerationRequest == null)) {
            throw new IllegalArgumentException("记忆空间版本、来源或代次状态不一致。");
        }
    }

    /**
     * 保留创建信息推进数据代次；是否有管理权限由调用该存储能力的上层管理服务校验。
     * @param requestId 当前管理请求 ID
     * @return 下一代不可变元数据
     * @throws NullPointerException 当请求 ID 为空时
     * @throws IllegalStateException 当版本或代次已无法递增时
     */
    public MemoryNamespace nextGeneration(UUID requestId) {
        Objects.requireNonNull(requestId, "换代请求标识不能为空。");
        try { return new MemoryNamespace(id, Math.incrementExact(revision), Math.incrementExact(generation), createdAt, creation, requestId); }
        catch (ArithmeticException overflow) { throw new IllegalStateException("记忆空间版本或代次已耗尽。"); }
    }

    /**
     * 可持久化的创建来源，不保存权限对象、Bean 或执行句柄。
     * <br>2026/11/03：允许可信管理来源，系统任务仍无创建身份。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param requestId 原始创建请求 ID
     * @param actor 用户或管理主体 UUID、client/scope 与真实操作来源
     * @author zExNocs
     * @date 2026/10/29
     * @since 4.5.0-l
     * @version 4.8.0-l
     */
    public record Creation(UUID requestId, AgentActorContext actor) {
        /**
         * 自建空间具有明确用户或管理创建者，系统任务不继承创建配额。
         * @param requestId 创建请求 ID
         * @param actor 可信发起者
         * @throws NullPointerException 当必需字段为空时
         * @throws IllegalArgumentException 当发起者属于无创建身份的系统任务时
         */
        public Creation {
            Objects.requireNonNull(requestId, "创建请求标识不能为空。");
            Objects.requireNonNull(actor, "记忆空间创建者不能为空。");
            // 2026/11/03：管理身份与平台用户分别审计，均不能借系统任务创建。
            if (actor.origin() == AgentActorContext.Origin.SYSTEM) {
                throw new IllegalArgumentException("自建记忆空间必须具有明确用户或管理主体。");
            }
        }
    }
}
