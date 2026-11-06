package org.zexnocs.teanekoagent.runtime.prompt;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekocore.logger.IFailureReason;

import java.io.Serial;
import java.util.Objects;

/**
 * Prompt 资料端口的固定失败类别，不导出源正文、权限数据或原异常链。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：向交付诊断提供固定资料失败原因，避免被通用异常覆盖。
 *
 * <br>2026/11/06：保留失败阶段及安全根因，避免配置或异步边界丢失诊断。
 *
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.5.0-l
 * @version 4.8.4-l
 */
public final class AgentPromptException extends RuntimeException implements IFailureReason {
    @Serial
    private static final long serialVersionUID = 1L;
    private final Reason reason;
    private final org.zexnocs.teanekocore.logger.@org.jspecify.annotations.Nullable FailureDiagnostic diagnostic;

    /**
     * 使用固定中文原因报告禁用、失效、不可用或完整原输入超限。
     * @param reason 明确失败类别
     * @throws NullPointerException 当类别为空时
     */
    public AgentPromptException(Reason reason) {
        this(reason, (org.zexnocs.teanekocore.logger.FailureDiagnostic) null);
    }

    /**
     * 2026/11/06：保留原故障的安全结构，不持有来源 Throwable 或正文。
     * @param reason 业务类别
     * @param failure 原始失败
     */
    public AgentPromptException(Reason reason, Throwable failure) {
        this(reason, org.zexnocs.teanekocore.logger.FailureDiagnostic.from(failure));
    }

    /**
     * @param reason 业务类别
     * @param diagnostic 可选安全快照
     */
    private AgentPromptException(Reason reason,
            org.zexnocs.teanekocore.logger.@org.jspecify.annotations.Nullable FailureDiagnostic diagnostic) {
        super(switch (Objects.requireNonNull(reason, "Prompt 资料失败类别不能为空。")) {
            case DISABLED -> "当前参考资料功能已关闭。";
            case CHANGED -> "当前参考资料的绑定或代次已变化。";
            case UNAVAILABLE -> "当前参考资料暂不可用。";
            case INPUT_TOO_LARGE -> "本次原始输入超过完整 Prompt 的字节预算。";
        });
        this.reason = reason;
        this.diagnostic = diagnostic;
    }

    /**
     * 提供固定类别，不要求上层解析异常文本。
     * @return 明确失败类别
     */
    public Reason reason() { return reason; }

    /** @return 2026/11/05：固定可公开原因，不包含资料正文、身份数据或配置值 */
    @Override
    public String failureReason() {
        return getMessage() + (diagnostic == null ? "" : " " + diagnostic.description());
    }

    /** @return 原类型链、HTTP 状态和位置，不输出来源内容。 */
    @Override
    public org.zexnocs.teanekocore.logger.@org.jspecify.annotations.Nullable FailureDiagnostic failureDiagnostic() {
        return diagnostic;
    }

    /**
     * 分离功能关闭、绑定变化、来源故障及原始输入超限。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @author zExNocs
     * @date 2026/10/29
     * @since 4.5.0-l
     * @version 4.8.0-l
     */
    public enum Reason { DISABLED, CHANGED, UNAVAILABLE, INPUT_TOO_LARGE }
}
