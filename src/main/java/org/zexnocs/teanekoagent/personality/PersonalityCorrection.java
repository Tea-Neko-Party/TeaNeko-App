package org.zexnocs.teanekoagent.personality;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.agent.AgentActorContext;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 具有真实来源的表达补正，不是个人画像事实，也不承载权限或身份规则。
 * @param requestId 稳定候选标识及幂等键
 * @param baseId 所属基础人格
 * @param baseVersion 候选依据的基础版本
 * @param field 代码允许的表达字段
 * @param text 完整补正正文，始终作为低信任数据
 * @param confidence 来源置信度
 * @param source 真实互动来源
 * @param occurredAt 来源时刻
 * @param expiresAt 可选有效期
 * @param locked 锁定来源，自动候选不能覆盖已锁定字段
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record PersonalityCorrection(UUID requestId, String baseId, int baseVersion, String field, String text, double confidence,
        AgentActorContext source, Instant occurredAt, @Nullable Instant expiresAt, boolean locked) {
    /**
     * 校验候选来源与绝对白名单，禁止以配置扩展名义学习身份、密钥、工具或用户事实。
     * @param requestId 原请求标识
     * @param baseId 基础人格
     * @param baseVersion 基础版本
     * @param field 表达字段
     * @param text 有界完整正文
     * @param confidence 有限置信度
     * @param source 真实发起者
     * @param occurredAt 来源时刻
     * @param expiresAt 可选到期时刻
     * @param locked 锁定标记，只有可信管理入口可以授予
     * @throws IllegalArgumentException 当字段、来源或范围无效时
     */
    public PersonalityCorrection {
        Objects.requireNonNull(requestId, "补正请求标识不能为空。"); Objects.requireNonNull(source, "补正来源不能为空。"); Objects.requireNonNull(occurredAt, "补正来源时刻不能为空。");
        if (baseId == null || baseId.isBlank() || baseId.length() > 128 || baseVersion < 1 || !BasePersonality.STYLE_FIELDS.contains(field)
                || text == null || text.isBlank() || text.length() > 512 || !Double.isFinite(confidence) || confidence < 0 || confidence > 1
                || source.origin() != AgentActorContext.Origin.USER || source.userId().isEmpty()
                || expiresAt != null && !expiresAt.isAfter(occurredAt)) { throw new IllegalArgumentException("人格补正字段、来源或范围无效。"); }
    }
    /**
     * 用同一明确时刻判断候选是否仍有效，锁定不能绕过到期。
     * @param now 本次固定时刻
     * @return 尚未到期时为 true
     */
    public boolean activeAt(Instant now) { return expiresAt == null || expiresAt.isAfter(now); }
}
