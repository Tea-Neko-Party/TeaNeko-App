package org.zexnocs.teanekoagent_old.agent.token;

import lombok.*;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * Agent token 使用记录。
 * <br>该对象只保存短摘要信息，适合写入 DebugEasyData 作为 token 使用日志；完整上下文内容由
 * {@link AgentTokenContextSnapshot} 独立写入 CleanableEasyData。
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
public class AgentTokenUsageRecord {
    /**
     * 单条 token 使用记录 ID。
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
     * prompt token 数。
     */
    private int promptTokens;

    /**
     * completion token 数。
     */
    private int completionTokens;

    /**
     * 总 token 数。
     */
    private int totalTokens;

    /**
     * prompt 缓存命中 token 数。
     */
    private int promptCacheHitTokens;

    /**
     * prompt 缓存未命中 token 数。
     */
    private int promptCacheMissTokens;

    /**
     * reasoning token 数。
     */
    private int reasoningTokens;

    /**
     * prompt 中的上下文消息数量。
     */
    private int contextMessageCount;

    /**
     * prompt 中可渲染文本的字符长度。
     */
    private int contextCharacterLength;

    /**
     * 配置的上下文窗口 token 上限。
     */
    @Nullable
    private Integer contextWindowTokens;

    /**
     * 估算剩余上下文 token。
     */
    @Nullable
    private Integer contextRemainingTokens;

    /**
     * 本次 completion 输出 token 上限。
     */
    @Nullable
    private Integer maxCompletionTokens;

    /**
     * 估算剩余 completion token。
     */
    @Nullable
    private Integer completionRemainingTokens;

    /**
     * prompt token 与上下文窗口上限的比例。
     */
    private double contextUsageRatio;

    /**
     * completion token 与输出上限的比例。
     */
    private double completionUsageRatio;

    /**
     * 该记录的 token 使用级别。
     */
    @Builder.Default
    private AgentTokenUsageLevel level = AgentTokenUsageLevel.NORMAL;

    /**
     * 上下文快照保留分类。
     */
    private String retentionCategory;

    /**
     * 是否已写入上下文快照。
     */
    private boolean contextSnapshotStored;

    /**
     * 上下文快照在 CleanableEasyData 中的 target。
     */
    @Nullable
    private String contextSnapshotTarget;

    /**
     * 上下文快照在 CleanableEasyData 中的 key。
     */
    @Nullable
    private String contextSnapshotKey;

    /**
     * 上下文快照过期时间。
     */
    @Nullable
    private Instant contextExpiresAt;

    /**
     * 记录创建时间。
     */
    @Builder.Default
    private Instant createdAt = Instant.now();
}
