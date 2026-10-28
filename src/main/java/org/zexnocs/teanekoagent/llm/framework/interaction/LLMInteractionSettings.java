package org.zexnocs.teanekoagent.llm.framework.interaction;

import java.time.Duration;
import java.util.Objects;

/**
 * 一次交互固定的限额与时限，不读取 Agent、平台或凭据配置。
 *
 * @param limits 本次交互局部限额，仍受宿主共享预算约束
 * @param modelTimeout 模型阶段上限，包含执行器排队
 * @param toolTimeout 单个工具阶段上限，包含执行器排队
 * @param turnTimeout 从创建交互执行开始计算的总时限
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record LLMInteractionSettings(LLMCallLimits limits, Duration modelTimeout,
                                     Duration toolTimeout, Duration turnTimeout) {
    /**
     * 固定不可变配置并拒绝无意义的阶段时限，不静默修正错误配置。
     *
     * @param limits 本次局部限额
     * @param modelTimeout 模型阶段时限
     * @param toolTimeout 工具阶段时限
     * @param turnTimeout 整次交互时限
     * @throws NullPointerException 当限额或任一时限为空时
     * @throws IllegalArgumentException 当任一时限非正时
     */
    public LLMInteractionSettings {
        Objects.requireNonNull(limits, "交互限额不能为空。");
        requirePositive(modelTimeout);
        requirePositive(toolTimeout);
        requirePositive(turnTimeout);
    }

    /**
     * 提供计划约定的初始值，不覆盖宿主传入的具体配置。
     *
     * @return 默认次数及模型六十秒、工具十五秒、整轮一百二十秒的配置
     */
    public static LLMInteractionSettings defaults() {
        return new LLMInteractionSettings(LLMCallLimits.defaults(), Duration.ofSeconds(60),
                Duration.ofSeconds(15), Duration.ofSeconds(120));
    }

    /**
     * 在配置进入执行层前检查时限，避免排队或资源等待缺少有效上界。
     *
     * @param duration 需要验证的阶段时限
     * @throws NullPointerException 当时限为空时
     * @throws IllegalArgumentException 当时限为零或负数时
     */
    private static void requirePositive(Duration duration) {
        Objects.requireNonNull(duration, "交互时限不能为空。");
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("交互时限必须大于零。");
        }
    }
}
