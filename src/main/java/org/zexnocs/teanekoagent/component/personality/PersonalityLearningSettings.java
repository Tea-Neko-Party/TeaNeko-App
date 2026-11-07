package org.zexnocs.teanekoagent.component.personality;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import java.time.Duration;
import java.util.Objects;

/**
 * 独立后台学习预算，应用补正的开关仍由人格配置决定。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @param enabled 是否允许自动生成
 * @param cooldown 同会话尝试冷却
 * @param timeout 整个生成及提交观察时限
 * @param maxModelCalls 最多实际模型请求次数
 * @param maxTokens 显式独立总 token 预算，未配置时为空
 * @param maxInputBytes 完整生成输入的协议字节上限
 * @param retention 候选有效期
 * <br>2026/11/04：将 token 预算改为可选，未配置时使用模型额度准入。
 * <br>2026/11/04：校正当前实现的版本归属为 v4.7.2-l。
 * <br>2026/11/07：根据原提交职责恢复编码损坏的历史注释。
 *
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.5.0-l
 * @version 4.8.0-l
 */
public record PersonalityLearningSettings(boolean enabled, Duration cooldown, Duration timeout, int maxModelCalls,
        @org.jspecify.annotations.Nullable Long maxTokens, long maxInputBytes, Duration retention) {
    /**
     * 拒绝无界后台开销及无效时间配置，不在构造时读取外部配置。
     * @param enabled 自动生成开关
     * @param cooldown 冷却时间
     * @param timeout 独立时限
     * @param maxModelCalls 请求上限
     * @param maxTokens 显式总 token 上限，可为空
     * @param maxInputBytes 完整协议上限
     * @param retention 补正有效期
     * @throws IllegalArgumentException 当预算或时间超出允许范围时
     */
    public PersonalityLearningSettings {
        Objects.requireNonNull(cooldown, "学习冷却不能为空。"); Objects.requireNonNull(timeout, "学习时限不能为空。"); Objects.requireNonNull(retention, "学习有效期不能为空。");
        if (cooldown.isNegative() || cooldown.compareTo(Duration.ofDays(1)) > 0 || timeout.isNegative() || timeout.isZero()
                // 2026/11/04：未声明总 token 配额时继承模型容量，显式零额度仍拒绝调用。
                || timeout.compareTo(Duration.ofMinutes(5)) > 0 || maxModelCalls < 1 || maxModelCalls > 10
                || maxTokens != null && maxTokens < 0
                || maxInputBytes < 1024 || maxInputBytes > 1048576 || retention.isNegative() || retention.isZero()
                || retention.compareTo(Duration.ofDays(365)) > 0) { throw new IllegalArgumentException("人格学习预算或时间配置无效。"); }
    }
}
