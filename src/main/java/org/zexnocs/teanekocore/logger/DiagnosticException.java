package org.zexnocs.teanekocore.logger;

import java.io.Serial;

/**
 * 用固定业务原因保留底层安全诊断，不向公开异常复制原始消息或响应正文。
 * <br>2026/11/06：用于必须隔离敏感来源异常、但不能丢失原因链和位置的边界。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.4-l
 * @version 4.8.4-l
 */
public final class DiagnosticException extends IllegalStateException implements IFailureReason {
    @Serial
    private static final long serialVersionUID = 1L;
    private final String reason;
    private final FailureDiagnostic diagnostic;

    /**
     * @param reason 调用处固定的安全业务原因，不得拼接原始输入或异常消息
     * @param cause 原始失败，仅提取有界诊断，不保留 Throwable
     */
    public DiagnosticException(String reason, Throwable cause) {
        super(reason);
        this.reason = reason;
        diagnostic = FailureDiagnostic.from(cause);
    }
    /** @return 固定业务原因及底层安全分类 */
    @Override public String failureReason() { return reason + " " + diagnostic.description(); }
    /** @return 原始类型链、HTTP 状态与代码位置 */
    @Override public FailureDiagnostic failureDiagnostic() { return diagnostic; }
}
