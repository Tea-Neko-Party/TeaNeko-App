package org.zexnocs.teanekoagent.agent.personality;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/**
 * 已解析的人格纯数据，基础设定与低信任补正分开；不保存服务或授权能力。
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
 * @since 4.0.0
 * @version 4.5.0-l
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
        if (identity.isBlank() || baseVersion < 1 || correctionRevision < 0 || instructions.isBlank()
                || instructions.length() > 65536 || adjustmentsJson.length() > 131072) { throw new IllegalArgumentException("人格快照范围或版本无效。"); }
    }
}
