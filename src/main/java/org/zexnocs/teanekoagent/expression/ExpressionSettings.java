package org.zexnocs.teanekoagent.expression;
/**
 * 每轮冻结的拟人数量与时序策略。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @param enabled 总开关
 * @param maxMessages 新信封消息上限
 * @param maxStepDelay 单段等待上限
 * @param maxTotalDelay 累计等待上限
 * @param defaultDelay 后续默认等待基数
 * @param timeoutSeconds 队列及执行总时限
 * @param maxQueued 单个发送目标排队上限
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.6.2-l
 */
public record ExpressionSettings(boolean enabled, int maxMessages, int maxStepDelay, int maxTotalDelay, int defaultDelay, int timeoutSeconds, int maxQueued) {
    /**
     * 限制本地配置，错误时拒绝本轮而非无限等待。
     * @param enabled 开关
     * @param maxMessages 消息上限
     * @param maxStepDelay 单段等待
     * @param maxTotalDelay 总等待
     * @param defaultDelay 默认等待
     * @param timeoutSeconds 时限
     * @param maxQueued 排队上限
     * @throws IllegalArgumentException 当配置范围无效时
     */
    public ExpressionSettings {
        if (maxMessages < 1 || maxMessages > 32 || maxStepDelay > 5000 || maxTotalDelay < 0 || maxTotalDelay > 30000
                || defaultDelay < 0 || defaultDelay > maxStepDelay || timeoutSeconds < 1 || timeoutSeconds > 180 || maxQueued < 1 || maxQueued > 128) {
            throw new IllegalArgumentException("拟人表达数量或时序配置无效。");
        }
    }
    /** @return 本地文件缺省的有界设置 */
    public static ExpressionSettings defaults() { return new ExpressionSettings(true, 8, 3000, 10000, 350, 90, 16); }
}
