package org.zexnocs.teanekoagent.memory.namespace;

import org.zexnocs.teanekoagent.agent.AgentActorContext;
import java.util.Objects;
import java.util.UUID;

/**
 * 可信宿主固定的空间绑定命令，scope 来自真实 actor，不接收第二份可替换的目标作用域。
 * @param requestId 宿主固定的操作 ID；重试须保留全部参数
 * @param actor 已在上层验证权限的真实操作者和来源
 * @param target 目标业务空间
 * @param expectedRevision 原始绑定版本；未绑定时为零
 * @param mode 创建并绑定或绑定已有空间
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record MemoryNamespaceBindingRequest(UUID requestId, AgentActorContext actor, MemoryNamespaceId target,
        long expectedRevision, Mode mode) {
    /**
     * 校验命令定位与创建标识，不把系统任务视为具有管理权限的用户。
     * @param requestId 原始操作 ID
     * @param actor 可信操作者
     * @param target 目标空间
     * @param expectedRevision 原始预期版本
     * @param mode 绑定方式
     * @throws NullPointerException 当必需字段为空时
     * @throws IllegalArgumentException 当身份、版本或创建目标不一致时
     */
    public MemoryNamespaceBindingRequest {
        Objects.requireNonNull(requestId, "绑定请求标识不能为空。");
        Objects.requireNonNull(actor, "绑定操作者不能为空。");
        Objects.requireNonNull(target, "绑定目标不能为空。"); Objects.requireNonNull(mode, "绑定方式不能为空。");
        if (expectedRevision < 0 || actor.origin() != AgentActorContext.Origin.USER
                || mode == Mode.CREATE_AND_BIND && !target.equals(MemoryNamespaceId.created(requestId))) {
            throw new IllegalArgumentException("记忆空间绑定的身份、版本或创建目标不一致。");
        }
    }

    /**
     * 分离创建新空间与绑定已有空间；绑定他人创建的空间不自动要求 DEBUG 或所有权。
     * @author zExNocs
     * @date 2026/10/29
     * @since 4.0.0
     * @version 4.5.0-l
     */
    public enum Mode { CREATE_AND_BIND, BIND_EXISTING }
}
