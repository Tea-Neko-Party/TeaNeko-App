package org.zexnocs.teanekoagent.memory.admin;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.agent.AgentActorContext;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceId;

import java.util.Objects;
import java.util.UUID;

/**
 * 宿主固定的记忆管理操作，保留真实操作者、原始预期版本和完整业务参数用于事务防重。
 * DEBUG 权限由可信入口检查，记录不能通过模型参数构造来源身份。
 * @param operationId 宿主固定操作 ID
 * @param actor 已认证操作者
 * @param namespace 目标或克隆源空间
 * @param generation 原始预期数据代次
 * @param revision 编辑时为事实版本，其他操作为空间版本
 * @param action 管理动作
 * @param recordId 编辑目标，其他操作为空
 * @param text 可选替换正文，不改变其他事实属性
 * @param locked 可选锁定状态
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record MemoryManagementCommand(UUID operationId, AgentActorContext actor, MemoryNamespaceId namespace,
        long generation, long revision, Action action, @Nullable UUID recordId, @Nullable String text, @Nullable Boolean locked) {
    /**
     * 拒绝不完整、跨动作混合或无真实用户的管理请求。
     * @param operationId 操作 ID
     * @param actor 真实用户来源
     * @param namespace 空间
     * @param generation 代次
     * @param revision 预期版本
     * @param action 动作
     * @param recordId 编辑记录
     * @param text 可选正文
     * @param locked 可选锁定值
     * @throws NullPointerException 当必要参数为空时
     * @throws IllegalArgumentException 当版本、来源或动作参数不合法时
     */
    public MemoryManagementCommand {
        Objects.requireNonNull(operationId, "管理操作 ID 不能为空。"); Objects.requireNonNull(actor, "管理来源不能为空。");
        Objects.requireNonNull(namespace, "管理空间不能为空。"); Objects.requireNonNull(action, "管理动作不能为空。");
        if (actor.origin() != AgentActorContext.Origin.USER || generation < 0 || revision < 0
                || (action == Action.EDIT ? recordId == null || text == null && locked == null : recordId != null || text != null || locked != null)
                || text != null && (text.isBlank() || text.length() > 4096)) {
            throw new IllegalArgumentException("记忆管理来源、版本或动作参数不合法。");
        }
    }
    /**
     * 管理动作不包含画像或人格修改，克隆只复制当前空间的事实证据快照。
     * @author zExNocs
     * @date 2026/11/01
     * @since 4.0.0
     * @version 4.5.0-l
     */
    public enum Action { EDIT, CLONE, CLEAR }
}
