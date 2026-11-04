package org.zexnocs.teanekoagent.component.personality;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/**
 * 已解析的人格纯数据，基础设定与低信任补正分开；不保存服务或授权能力。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：提供无人格身份分区，独立记忆不注入人格提示。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @param identity 基础设定、绑定和应用开关共同决定的会话身份
 * @param baseId 基础人格标识
 * @param baseVersion 基础人格版本
 * @param correctionKey 补正空间
 * @param correctionRevision 证据快照版本，不用于中断同轮已固定的补正
 * @param instructions 可信配置派生的基础人格，不能覆盖宿主运行规则
 * @param adjustmentsJson 仅作为数据呈现的有界补正及来源 JSON
 * @param expiresAt 本轮已采用补正的最早到期时刻
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.5.0-l
 * @version 4.8.0-l
 */
public record AgentPersonalitySnapshot(String identity, String baseId, int baseVersion, String correctionKey,
        long correctionRevision, String instructions, String adjustmentsJson, @Nullable Instant expiresAt) {
    /**
     * 校验固定身份和有界文本，不从补正正文重新生成运行规则。
     * @param identity 会话人格身份
     * @param baseId 基础人格标识
     * @param baseVersion 基础版本
     * @param correctionKey 补正定位
     * @param correctionRevision 补正快照版本
     * @param instructions 可信基础设定
     * @param adjustmentsJson 低信任补正数据
     * @param expiresAt 最早补正到期时刻
     * @throws IllegalArgumentException 当版本或文本范围无效时
     * @throws NullPointerException 当必需值为空时
     */
    public AgentPersonalitySnapshot {
        Objects.requireNonNull(identity, "人格身份不能为空。"); Objects.requireNonNull(baseId, "基础人格标识不能为空。"); Objects.requireNonNull(correctionKey, "补正定位不能为空。");
        Objects.requireNonNull(instructions, "基础人格设定不能为空。"); Objects.requireNonNull(adjustmentsJson, "补正数据不能为空。");
        if (identity.isBlank() || baseVersion < 1 || correctionRevision < 0 || instructions.isBlank() && !baseId.equals("__none__")
                || instructions.length() > 65536 || adjustmentsJson.length() > 131072) { throw new IllegalArgumentException("人格快照范围或版本无效。"); }
    }

    /**
     * 2026/11/04：独立记忆使用无人格分区，不读取或创建人格存储，也不产生提示文本。
     * @param scope 实际聊天定位
     * @return 显式未装配人格的身份标记
     */
    public static AgentPersonalitySnapshot absent(org.zexnocs.teanekoagent.runtime.AgentScope scope) {
        String identity = "no-personality:" + org.zexnocs.teanekocore.utils.StableDigest.sha256(scope.clientId(), scope.scopeId());
        return new AgentPersonalitySnapshot(identity, "__none__", 1, identity, 0, "", "", null);
    }

    /** @return 2026/11/04：是否实际安装人格，空身份标记不能进入模型提示。 */
    public boolean present() {
        return !baseId.equals("__none__");
    }
}
