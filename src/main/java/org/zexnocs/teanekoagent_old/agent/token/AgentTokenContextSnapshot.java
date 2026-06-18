package org.zexnocs.teanekoagent_old.agent.token;

import lombok.*;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Agent token 上下文快照。
 * <br>该对象写入 CleanableEasyData，用于短期保存一次模型调用对应的上下文内容，方便调试 token 消耗。
 *
 * @author zExNocs
 * @date 2026/06/11
 * @since 4.4.1
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTokenContextSnapshot {
    /**
     * 关联的 token 使用记录 ID。
     */
    private String usageId;

    /**
     * 模型 API 标识。
     */
    private String api;

    /**
     * 模型适配器或供应商 ID。
     */
    private String provider;

    /**
     * 实际响应模型名称。
     */
    private String model;

    /**
     * 作用域 ID。
     */
    private String scopeId;

    /**
     * agent ID。
     */
    private String agentId;

    /**
     * 用户 ID。
     */
    private String userId;

    /**
     * 会话 ID。
     */
    private String conversationId;

    /**
     * 请求消息 ID。
     */
    private String requestMessageId;

    /**
     * 当前模型调用轮次。
     */
    private int round;

    /**
     * token 使用级别。
     */
    @Builder.Default
    private AgentTokenUsageLevel level = AgentTokenUsageLevel.NORMAL;

    /**
     * 总 token 数。
     */
    private int totalTokens;

    /**
     * 原始上下文可渲染文本字符数。
     */
    private int originalCharacterLength;

    /**
     * 实际保存的上下文可渲染文本字符数。
     */
    private int storedCharacterLength;

    /**
     * 上下文快照是否被截断。
     */
    private boolean truncated;

    /**
     * 渲染后的上下文消息列表。
     */
    @Builder.Default
    private List<String> messages = new ArrayList<>();

    /**
     * 快照创建时间。
     */
    @Builder.Default
    private Instant createdAt = Instant.now();

    /**
     * 快照过期时间。
     * <br>为 {@code null} 时表示不由 token 监控器自动清理。
     */
    @Nullable
    private Instant expiresAt;
}
