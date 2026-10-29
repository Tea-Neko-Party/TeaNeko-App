package org.zexnocs.teanekoagent.agent.summary;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.agent.conversation.AgentConversationStamp;
import org.zexnocs.teanekoagent.agent.personality.AgentPersonalitySnapshot;
import org.zexnocs.teanekoagent.llm.framework.message.LLMMessageCodec;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceReference;
import org.zexnocs.teanekocore.framework.composition.ComponentKey;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 摘要服务生成的只读压缩状态，扩展实现经显式工厂构造；数据自身不授予业务授权。
 * @author zExNocs
 * @date 2026/10/30
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Getter @Accessors(fluent = true) @JsonIgnoreType
public final class AgentSummarySnapshot {
    public static final ComponentKey<AgentSummarySnapshot> COMPONENT = ComponentKey.of("agent.conversation-summary", AgentSummarySnapshot.class);
    private final UUID id = UUID.randomUUID();
    private final @Nullable UUID baseId;
    private final AgentConversationStamp stamp;
    private final MemoryNamespaceReference reference;
    private final AgentPersonalitySnapshot personality;
    private final List<Coverage> covered;
    private final String text;
    private final Instant expiresAt;
    private final boolean partial;
    /**
     * 供自定义摘要端口建立有明确覆盖的状态，使用前仍由实际端口复核业务权限和有效期。
     * @param base 已接受的旧摘要
     * @param stamp 实际代次
     * @param reference 实际引用
     * @param personality 实际人格
     * @param covered 新增完整轮次指纹
     * @param text 有界正文
     * @param expiresAt 到期时间
     * @param partial 是否存在覆盖缺口
     * @return 冻结的摘要状态
     * @throws IllegalArgumentException 当正文、覆盖或边界不合法时
     */
    public static AgentSummarySnapshot create(@Nullable AgentSummarySnapshot base, AgentConversationStamp stamp, MemoryNamespaceReference reference,
            AgentPersonalitySnapshot personality, List<Coverage> covered, String text, Instant expiresAt, boolean partial) {
        Objects.requireNonNull(stamp, "摘要代次不能为空。"); Objects.requireNonNull(reference, "摘要引用不能为空。");
        Objects.requireNonNull(personality, "摘要人格不能为空。"); Objects.requireNonNull(expiresAt, "摘要期限不能为空。");
        covered = List.copyOf(covered);
        if (text == null || text.isBlank() || text.length() > 16000 || covered.isEmpty() || covered.size() > 100
                || covered.stream().map(Coverage::requestId).distinct().count() != covered.size()
                || covered.stream().anyMatch(item -> item.requestId() == null || item.signature() == null)
                || !reference.scope().equals(stamp.key().scope()) || base != null && (!base.stamp().equals(stamp)
                    || !base.reference().equals(reference) || !base.personality().identity().equals(personality.identity()))) {
            throw new IllegalArgumentException("摘要正文、覆盖或继承边界无效。");
        }
        return new AgentSummarySnapshot(base == null ? null : base.id(), stamp, reference, personality, covered, text, expiresAt, partial);
    }
    /**
     * 冻结一次已校验生成结果，只保存覆盖指纹，不重复缓存原消息或完整交互。
     * @param baseId 本次继承的旧摘要 ID
     * @param stamp 生成时会话代次
     * @param reference 原空间引用
     * @param personality 原人格
     * @param covered 本次新增覆盖的完整轮次
     * @param text 有界正文
     * @param expiresAt 有效期
     * @param partial 是否此前存在未被覆盖的窗口丢弃
     */
    AgentSummarySnapshot(@Nullable UUID baseId, AgentConversationStamp stamp, MemoryNamespaceReference reference,
            AgentPersonalitySnapshot personality, List<Coverage> covered, String text, Instant expiresAt, boolean partial) {
        this.baseId = baseId; this.stamp = stamp; this.reference = reference; this.personality = personality;
        this.covered = List.copyOf(covered); this.text = Objects.requireNonNull(text, "摘要正文不能为空。"); this.expiresAt = expiresAt; this.partial = partial;
    }
    /**
     * 一个已覆盖完整轮次的身份，不保存工具、原文或任何执行资源。
     * @param requestId 原始请求
     * @param signature 完整协议指纹
     * @author zExNocs
     * @date 2026/10/30
     * @since 4.0.0
     * @version 4.5.0-l
     */
    public record Coverage(UUID requestId, LLMMessageCodec.Signature signature) {}
}
