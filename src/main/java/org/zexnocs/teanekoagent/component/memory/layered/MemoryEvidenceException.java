package org.zexnocs.teanekoagent.component.memory.layered;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.component.llm.framework.interaction.LLMInteractionResult;
import org.zexnocs.teanekocore.logger.IFailureReason;
import org.zexnocs.teanekocore.utils.StableDigest;

import java.io.Serial;
import java.util.UUID;

/**
 * 长期候选的安全证据诊断，不持有原话、模型正文或未经筛选的异常消息。
 * <br>2026/11/05：区分候选关系错误与领取后的来源失效。
 *
 * @author zExNocs
 * @date 2026/11/05
 * @since 4.8.3-l
 * @version 4.8.3-l
 */
public final class MemoryEvidenceException extends IllegalStateException implements IFailureReason {
    @Serial
    private static final long serialVersionUID = 1L;
    private final Code code;
    private final Domain domain;
    private final int index;
    private final UUID batchId;
    private final String evidenceTag;
    private final LLMInteractionResult.@Nullable Status modelStatus;

    /**
     * 创建不含正文的证据定位信息。
     * @param code 固定原因
     * @param domain 候选所属数组
     * @param index 从零开始的候选序号
     * @param batchId 原租约编号
     * @param evidenceId 原证据标识，仅保存不可逆短摘要
     */
    public MemoryEvidenceException(Code code, Domain domain, int index, UUID batchId, @Nullable String evidenceId) {
        this(code, domain, index, batchId,
                evidenceId == null ? "无" : StableDigest.sha256(evidenceId).substring(0, 12), null);
    }

    /**
     * 复制安全元数据，不把完整原异常挂到公开诊断链。
     * @param code 固定原因
     * @param domain 候选域
     * @param index 候选序号
     * @param batchId 批次
     * @param evidenceTag 已脱敏定位
     * @param modelStatus 实际观察的模型状态
     */
    private MemoryEvidenceException(Code code, Domain domain, int index, UUID batchId, String evidenceTag,
            LLMInteractionResult.@Nullable Status modelStatus) {
        super(code.description());
        this.code = code;
        this.domain = domain;
        this.index = index;
        this.batchId = batchId;
        this.evidenceTag = evidenceTag;
        this.modelStatus = modelStatus;
    }

    /** @return 固定证据原因码 */
    public Code code() { return code; }

    /** @return 候选所属域 */
    public Domain domain() { return domain; }

    /** @return 从零开始的候选序号 */
    public int index() { return index; }

    /** @return 实际观察的模型状态，事务直接调用时未知 */
    public LLMInteractionResult.@Nullable Status modelStatus() { return modelStatus; }

    /**
     * 仅由已经取得结果的生成路径补充模型状态。
     * @param status 实际状态
     * @return 同一诊断的安全副本
     */
    public MemoryEvidenceException observed(LLMInteractionResult.Status status) {
        return new MemoryEvidenceException(code, domain, index, batchId, evidenceTag, status);
    }

    /** @return 可公开的固定原因、候选位置与脱敏证据定位 */
    @Override
    public String failureReason() {
        return code.name() + "（" + code.description() + "），候选=" + domain.name().toLowerCase(java.util.Locale.ROOT)
                + "[" + index + "]，批次=" + batchId + "，证据摘要=" + evidenceTag;
    }

    /**
     * 区分事实与人格观察，避免仅凭长期日志标签误判。
     * @author zExNocs
     * @date 2026/11/05
     * @since 4.8.3-l
     * @version 4.8.3-l
     */
    public enum Domain { MEMORIES, STYLES }

    /**
     * 稳定原因与重试分类；来源失效不计为模型重复生成错误。
     * @author zExNocs
     * @date 2026/11/05
     * @since 4.8.3-l
     * @version 4.8.3-l
     */
    public enum Code {
        EVIDENCE_ID_INVALID("引用的证据不属于本批", true),
        SEGMENT_OUTSIDE_BATCH("候选片段不属于本批", true),
        QUOTE_MISMATCH("引用不是证据原文的逐字连续片段", true),
        SUBJECT_MISMATCH("候选主体与证据作者不一致", true),
        SEGMENT_MISMATCH("证据不在候选选择的片段范围内", true),
        TIME_HINT_MISMATCH("重要时间未逐字出现在已核对引用中", true),
        STYLE_TARGET_MISMATCH("风格字段或证据不属于当前观察目标", true),
        EVIDENCE_MISSING("原证据已不存在", false),
        EVIDENCE_CHANGED("原证据与领取时快照不一致", false),
        EVIDENCE_EXPIRED("原证据已超过保留期", false),
        EVIDENCE_ROLE_INVALID("证据不是真实用户消息", false),
        STYLE_TARGET_CHANGED("原证据的人格依据已改变", false);

        private final String description;
        private final boolean invalidCandidate;

        /**
         * @param description 固定中文说明
         * @param invalidCandidate 是否属于候选关系错误
         */
        Code(String description, boolean invalidCandidate) {
            this.description = description;
            this.invalidCandidate = invalidCandidate;
        }

        /** @return 固定中文说明 */
        public String description() { return description; }

        /** @return 是否应计入同输入的连续无效生成次数 */
        public boolean invalidCandidate() { return invalidCandidate; }
    }
}
