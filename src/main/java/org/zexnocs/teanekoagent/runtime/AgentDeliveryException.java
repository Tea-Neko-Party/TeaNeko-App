package org.zexnocs.teanekoagent.runtime;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekocore.logger.FailureDiagnostic;
import org.zexnocs.teanekocore.logger.IFailureReason;

import java.io.Serial;

/**
 * 发送前复核的安全原因，区分结果状态、原计划和目标变化，不记录聊天正文。
 *
 * @author zExNocs
 * @date 2026/11/05
 * @since 4.8.3-l
 * @version 4.8.3-l
 */
public final class AgentDeliveryException extends IllegalStateException implements IFailureReason {
    @Serial
    private static final long serialVersionUID = 1L;
    private final transient @Nullable FailureDiagnostic diagnostic;

    /**
     * @param checked 原结果复核返回的真实状态，不以通用错误掩盖失败分类
     */
    public AgentDeliveryException(AgentRunResult checked) {
        super("发送前复核未通过，状态=" + checked.status() + "；" + checked.failureDescription());
        diagnostic = checked.failure();
    }

    /** @param reason 宿主固定说明，不允许传入聊天或模型原文 */
    public AgentDeliveryException(String reason) {
        super(reason);
        diagnostic = null;
    }

    /** @return 可公开的原失败状态或目标变化说明 */
    @Override
    public String failureReason() {
        return getMessage();
    }

    /** @return 原复核结果已携带的安全诊断，没有时为空 */
    @Override
    public @Nullable FailureDiagnostic failureDiagnostic() {
        return diagnostic;
    }
}
