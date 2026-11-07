package org.zexnocs.teanekocore.actuator.execution;

import java.io.Serial;

/**
 * 有界入口拒绝新工作，区别于配置错误或已经执行但结果未知。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.7-l
 * @version 4.8.7-l
 */
public final class ExecutionBusyException extends IllegalStateException {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * @param reason 入口生成的固定容量说明，不拼接请求内容
     */
    public ExecutionBusyException(String reason) {
        super(reason);
    }
}
