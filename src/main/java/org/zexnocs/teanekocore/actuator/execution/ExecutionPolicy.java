package org.zexnocs.teanekocore.actuator.execution;

import java.time.Duration;
import java.util.Objects;

/**
 * 阶段时限及借用的执行控制；作为独立调用参数传递，不属于业务数据或模型选项。
 *
 * @param timeout 阶段时限；按接口约定可为空
 * @param control 借用的取消、截止时间和预算控制
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record ExecutionPolicy(Duration timeout, ExecutionControl control) {
    /**
     * 校验可选阶段时限；策略仅借用执行控制，不承担其关闭职责。
     *
     * @param timeout 阶段时限；按接口约定可为空
     * @param control 借用的取消、截止时间和预算控制
     * @throws IllegalArgumentException 当参数格式、范围或声明的类型约束不满足时
     */
    public ExecutionPolicy {
        if (timeout != null && (timeout.isZero() || timeout.isNegative())) {
            throw new IllegalArgumentException("Execution timeout must be positive.");
        }
    }

    /**
     * 创建没有显式阶段时限和执行控制的策略，由资源适配器选择默认值。
     *
     * @return 固定阶段时限与借用控制的策略
     */
    public static ExecutionPolicy defaults() {
        return new ExecutionPolicy(null, null);
    }

    /**
     * 创建仅覆盖阶段时限的策略，不建立新的执行链路或资源预算。
     *
     * @param timeout 阶段时限；按接口约定可为空
     * @return 固定阶段时限与借用控制的策略
     * @throws NullPointerException 当必需的输入或依赖为空时
     */
    public static ExecutionPolicy withTimeout(Duration timeout) {
        return new ExecutionPolicy(Objects.requireNonNull(timeout, "timeout"), null);
    }

    /**
     * 将可选阶段时限与已有控制组合，保留父执行的取消和预算边界。
     *
     * @param timeout 阶段时限；按接口约定可为空
     * @param control 借用的取消、截止时间和预算控制
     * @return 固定阶段时限与借用控制的策略
     * @throws NullPointerException 当必需的输入或依赖为空时
     */
    public static ExecutionPolicy controlled(Duration timeout, ExecutionControl control) {
        return new ExecutionPolicy(timeout, Objects.requireNonNull(control, "control"));
    }

    /**
     * 在配置了执行控制时检查停止状态，防止已取消的阶段继续启动资源操作。
     *
     * @throws ExecutionStoppedException 当执行已取消、关闭或超过截止时间时
     */
    public void checkActive() {
        if (control != null) {
            control.checkActive();
        }
    }

    /**
     * 显式阶段时限覆盖默认值，再由整轮剩余时间收束。
     *
     * @param fallback 未显式配置时使用的可信默认值
     * @return 满足本方法约束的 {@code Duration} 结果
     * @throws IllegalArgumentException 当参数格式、范围或声明的类型约束不满足时
     * @throws NullPointerException 当必需的输入或依赖为空时
     * @throws ExecutionStoppedException 当执行已取消、关闭或超过截止时间时
     */
    public Duration effectiveTimeout(Duration fallback) {
        checkActive();
        var effective = timeout == null ? Objects.requireNonNull(fallback, "fallback") : timeout;
        if (effective.isZero() || effective.isNegative()) {
            throw new IllegalArgumentException("Execution timeout must be positive.");
        }
        if (control != null) {
            var remaining = control.remainingTime();
            if (remaining.isPresent() && remaining.get().compareTo(effective) < 0) {
                effective = remaining.get();
            }
            control.checkActive();
        }
        return effective;
    }
}
