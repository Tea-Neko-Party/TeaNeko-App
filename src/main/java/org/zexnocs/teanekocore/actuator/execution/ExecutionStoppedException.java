package org.zexnocs.teanekocore.actuator.execution;

/**
 * 可区分主动取消、截止时间与拥有者关闭的终止原因。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public final class ExecutionStoppedException extends RuntimeException {
    private final ExecutionControl.StopReason reason;

    /**
     * 固定首次生效的停止原因，供异步边界保留取消、关闭与截止时间的区别。
     *
     * @param reason 首次生效的终止原因
     */
    public ExecutionStoppedException(ExecutionControl.StopReason reason) {
        super("Execution stopped: " + reason);
        this.reason = reason;
    }

    /**
     * 提供首次生效的停止原因，供调用方区分取消、关闭和截止时间。
     *
     * @return 满足本方法约束的 {@code ExecutionControl.StopReason} 结果
     */
    public ExecutionControl.StopReason reason() {
        return reason;
    }
}
