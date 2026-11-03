package org.zexnocs.teanekoagent.memory.layered;

import java.time.Duration;

/**
 * 分层记忆的本地工作量和时限，作用域权限由原配置独立决定。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @param enabled 是否启用分层记忆
 * @param triggerMessages 自动压缩触发消息数
 * @param keepMessages 保留近期原始消息数
 * @param batchMessages 单批压缩上限
 * @param maxInputBytes 实际压缩输入字节上限
 * @param timeout 模型及提交观察总时限
 * @param retention 中期保留期
 * @param triggerBytes 原始消息字节触发阈值
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.1-l
 * @version 4.6.1-l
 */
public record LayeredMemorySettings(boolean enabled, int triggerMessages, int keepMessages, int batchMessages,
                                    int maxInputBytes, Duration timeout, Duration retention, int triggerBytes) {
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
     * @throws IllegalArgumentException 当预算或时间无效时
     */
    public LayeredMemorySettings {
        if (keepMessages < 2 || triggerMessages <= keepMessages || triggerMessages > 200 || batchMessages < 1 || batchMessages > 64
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
