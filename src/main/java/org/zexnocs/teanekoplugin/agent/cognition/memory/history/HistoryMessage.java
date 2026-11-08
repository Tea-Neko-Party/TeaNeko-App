package org.zexnocs.teanekoplugin.agent.cognition.memory.history;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoagent.runtime.AgentActorContext;
import java.time.Instant;
import java.util.Objects;

/**
 * 可检索的文本历史，角色与触发来源分开；不包含模型内部状态或运行资源。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
 *
 * @param messageId 稳定来源标识，角色一起参与幂等
 * @param source 真实来源或答案的触发来源，不将答案归为该用户发言
 * @param role 消息角色
 * @param text 纯文本
 * @param occurredAt 发生时间或明确标注的处理时间估算
 * @param estimatedTime 是否使用处理时间估算
 * @param truncated 是否因历史正文硬上限省略尾部
 *
 * @author zExNocs
 * @date 2026/10/30
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
public record HistoryMessage(String messageId, AgentActorContext source, Role role, String text, Instant occurredAt,
                             boolean estimatedTime, boolean truncated) {
    /**
     * 校验来源、角色及容量，不从历史正文恢复身份权限。
     * @param messageId 原消息 ID
     * @param source 宿主来源
     * @param role 真实角色
     * @param text 正文，最多 65536 字符
     * @param occurredAt 发生或估算时间
     * @param estimatedTime 时间依据
     * @param truncated 正文是否省略
     * @throws IllegalArgumentException 当正文或用户来源无效时
     */
    public HistoryMessage {
        Objects.requireNonNull(messageId, "历史消息标识不能为空。"); Objects.requireNonNull(source, "历史来源不能为空。");
        Objects.requireNonNull(role, "历史角色不能为空。"); Objects.requireNonNull(text, "历史正文不能为空。"); Objects.requireNonNull(occurredAt, "历史时间不能为空。");
        occurredAt = occurredAt.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        if (messageId.isBlank() || messageId.length() > 256 || text.isBlank() || text.length() > 65536
                || role == Role.USER && (source.origin() != AgentActorContext.Origin.USER || source.userId().isEmpty() || !messageId.equals(source.sourceId()))) {
            throw new IllegalArgumentException("历史标识、正文容量或真实用户来源无效。");
        }
    }
    /**
     * 幂等比较忽略重新观察的处理时间，真实事件时间仍须一致。
     * @param other 同一消息的重投
     * @return 内容及来源一致时为真
     */
    public boolean sameContent(HistoryMessage other) {
        return messageId.equals(other.messageId) && source.equals(other.source) && role == other.role && text.equals(other.text)
                && estimatedTime == other.estimatedTime && truncated == other.truncated && (estimatedTime || occurredAt.equals(other.occurredAt));
    }
    /**
     * 区分用户原文与尚未确认平台发送的 Agent 答案。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
     *
     *
     * @author zExNocs
     * @date 2026/10/30
     * @since 4.5.0-l
     * @version 4.9.0-l
     */
    public enum Role { USER, ASSISTANT }
}
