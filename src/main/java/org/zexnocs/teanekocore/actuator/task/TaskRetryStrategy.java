package org.zexnocs.teanekocore.actuator.task;

/**
 * 任务重试策略枚举类。
 * 结果 false 指的是当 result.isSuccess() 返回 false
 * 异常 指的是抛出任何异常是否进行重试
 * 此外，如果抛出 RetryException 则一定重试，如果抛出 NoRetryException 则一定不重试。
 * @see org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult
 * @see org.zexnocs.teanekocore.actuator.task.exception.TaskRetryException
 * @see org.zexnocs.teanekocore.actuator.task.exception.TaskNoRetryException
 * @author zExNocs
 * @date 2026/02/12
 */
public enum TaskRetryStrategy {
    /**
     * 结果 false: 不重试
     * 异常: 不重试
     * 00
     */
     NO_RETRY(0),

    /**
     * 结果 false: 重试
     * 异常: 不重试
     * 01
     */
    ONLY_FALSE(1),

    /**
     * 结果 false: 不重试
     * 异常: 重试
     * 10
     */
    ONLY_EXCEPTION(2),

    /**
     * 结果 false: 重试
     * 异常: 重试
     */
    ALWAYS_RETRY(3);

    /// 二进制位表示，第一位表示结果是否重试，第二位表示异常是否重试
    private final int code;

    TaskRetryStrategy(int code) {
        this.code = code;
    }

    /**
     * 是否在结果 false 时重试
     * @return true 如果在结果 false 时重试，否则 false
     */
    public boolean isRetryOnFalse() {
        return (code & 1) != 0;
    }

    /**
     * 是否在抛出异常时重试
     * @return true 如果在抛出异常时重试，否则 false
     */
    public boolean isRetryOnException() {
        return (code & 2) != 0;
    }
}
