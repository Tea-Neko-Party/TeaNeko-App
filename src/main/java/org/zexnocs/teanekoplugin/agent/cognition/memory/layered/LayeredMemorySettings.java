package org.zexnocs.teanekoplugin.agent.cognition.memory.layered;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoagent.llm.framework.interaction.LLMCallLimits;

import java.time.Duration;

/**
 * 分层记忆的本地工作量和时限，作用域权限由原配置独立决定。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：统一输入估算、输出上限与后台交互总预算，避免固定额度阻塞合法批次。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/06：按有效新证据聚合长期提取，保留版本幂等、租约和事务校验。
 * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
 *
 * @param enabled 是否启用分层记忆
 * @param triggerMessages 自动压缩触发消息数
 * @param keepMessages 保留近期原始消息数
 * @param batchMessages 单批压缩上限
 * @param maxInputBytes 实际压缩输入字节上限
 * @param timeout 模型及提交观察总时限
 * @param retention 中期保留期
 * @param triggerBytes 原始消息字节触发阈值
 * @param learning 长期增量提取策略
 *
 *
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.1-l
 * @version 4.9.0-l
 */
public record LayeredMemorySettings(boolean enabled, int triggerMessages, int keepMessages, int batchMessages,
                                    int maxInputBytes, Duration timeout, Duration retention, int triggerBytes,
                                    LongMemoryLearningPolicy learning) {
    /**
     * 2026/11/06：旧宿主构造使用默认长期聚合策略。
     * @param enabled 总开关
     * @param triggerMessages 短期触发数量
     * @param keepMessages 保留数量
     * @param batchMessages 短期批量
     * @param maxInputBytes 输入容量
     * @param timeout 执行时限
     * @param retention 保留期限
     * @param triggerBytes 字节触发阈值
     */
    public LayeredMemorySettings(boolean enabled, int triggerMessages, int keepMessages, int batchMessages,
            int maxInputBytes, Duration timeout, Duration retention, int triggerBytes) {
        this(enabled, triggerMessages, keepMessages, batchMessages, maxInputBytes, timeout, retention,
                triggerBytes, LongMemoryLearningPolicy.defaults());
    }
    /**
     * 2026/11/04：保留一次无工具请求约束，输入及输出 token 容量由实际模型配置决定。
     *
     * @return 不含隐式 token 配额的单次模型调用预算
     */
    public LLMCallLimits modelCallLimits() {
        return new LLMCallLimits(1, 0, 0);
    }

    /**
     * 沿用默认字节触发的显式宿主构造。
     * @param enabled 总开关
     * @param triggerMessages 消息触发阈值
     * @param keepMessages 近期保留数
     * @param batchMessages 单批上限
     * @param maxInputBytes 输入字节上限
     * @param timeout 总时限
     * @param retention 中期保留期
     */
    public LayeredMemorySettings(boolean enabled, int triggerMessages, int keepMessages, int batchMessages,
            int maxInputBytes, Duration timeout, Duration retention) {
        this(enabled, triggerMessages, keepMessages, batchMessages, maxInputBytes, timeout, retention, 32768);
    }
    /**
     * 拒绝无界设置，租约必须覆盖本次模型时限。
     * @param enabled 总开关
     * @param triggerMessages 触发数量
     * @param keepMessages 近期保留数量
     * @param batchMessages 批次上限
     * @param maxInputBytes 输入字节上限
     * @param timeout 工作时限
     * @param retention 中期保留期
     * @param triggerBytes 字节触发阈值
     * @param learning 长期聚合与质量过滤
     * @throws IllegalArgumentException 当预算或时间无效时
     */
    public LayeredMemorySettings {
        if (learning == null || keepMessages < 2 || triggerMessages <= keepMessages || triggerMessages > 200 || batchMessages < 1 || batchMessages > 64
                || maxInputBytes < 4096 || maxInputBytes > 262144 || timeout == null || timeout.isNegative() || timeout.isZero()
                || timeout.compareTo(Duration.ofMinutes(2)) > 0 || retention == null || retention.compareTo(Duration.ofDays(1)) < 0
                || retention.compareTo(Duration.ofDays(365)) > 0 || triggerBytes < 1024 || triggerBytes > maxInputBytes) {
            throw new IllegalArgumentException("分层记忆工作预算或保留期无效。");
        }
    }
    /** @return 默认十二条触发、保留四条、单批十六条的有界策略 */
    public static LayeredMemorySettings defaults() {
        return new LayeredMemorySettings(true, 12, 4, 16, 131072, Duration.ofSeconds(45), Duration.ofDays(90));
    }
}
