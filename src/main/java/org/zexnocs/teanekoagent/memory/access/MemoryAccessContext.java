package org.zexnocs.teanekoagent.memory.access;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.NonNull;
import org.zexnocs.teanekoagent.agent.AgentActorContext;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceReference;

import java.util.Objects;
import java.util.UUID;

/**
 * 宿主固定的记忆访问身份，不接受模型参数中的空间、操作者或授权声明。
 * @param requestId 原始宿主请求标识
 * @param actor 真实发起者及来源消息
 * @param reference 本轮已经固定的完整空间引用
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
@JsonIgnoreType
public record MemoryAccessContext(UUID requestId, AgentActorContext actor, MemoryNamespaceReference reference) {
    /**
     * 保持来源与空间作用域一致；构造本身不授予权限，实际操作仍须动态复核。
     * @param requestId 原请求标识
     * @param actor 可信身份
     * @param reference 固定空间引用
     * @throws NullPointerException 当必需字段为空时
     * @throws IllegalArgumentException 当空间与来源作用域不一致时
     */
    public MemoryAccessContext {
        Objects.requireNonNull(requestId, "记忆访问请求标识不能为空。");
        Objects.requireNonNull(actor, "记忆访问身份不能为空。");
        Objects.requireNonNull(reference, "记忆访问引用不能为空。");
        if (!reference.scope().equals(actor.scope())) { throw new IllegalArgumentException("记忆访问身份与空间作用域不一致。"); }
    }

    /**
     * 防止通用日志或 JSON 输出泄漏绑定与来源，模型参数不能由此对象反向产生。
     * @return 固定脱敏说明
     */
    @Override @JsonValue @NonNull
    public String toString() { return "记忆访问上下文[已隐藏可信绑定]"; }
}
