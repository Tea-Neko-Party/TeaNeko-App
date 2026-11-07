package org.zexnocs.teanekoagent.component.memory.summary;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import java.time.Duration;

/**
 * 独立摘要预算与触发规则，构造校验在实际读取时进行，不阻止非 Agent 功能启动。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @param enabled 真实登记且允许自动摘要
 * @param triggerTurns 触发轮次数
 * @param keepTurns 保留最近完整轮次数
 * @param triggerBytes 触发协议字节数
 * @param maxInputBytes 摘要输入完整协议字节上限
 * @param maxCharacters 摘要正文上限
 * @param maxModelCalls 独立交互模型请求上限
 * @param maxTokens 显式独立交互总 token 预算，未配置时为空
 * @param timeout 独立后台总时限
 * @param cooldown 同会话生成冷却
 * <br>2026/11/04：将 token 预算改为可选，未配置时使用模型额度准入。
 * <br>2026/11/04：校正当前实现的版本归属为 v4.7.2-l。
 * <br>2026/11/07：根据原提交职责恢复编码损坏的历史注释。
 *
 * @author zExNocs
 * @date 2026/10/30
 * @since 4.5.0-l
 * @version 4.8.0-l
 */
public record AgentSummarySettings(boolean enabled, int triggerTurns, int keepTurns, long triggerBytes, long maxInputBytes,
        int maxCharacters, int maxModelCalls, @org.jspecify.annotations.Nullable Long maxTokens, Duration timeout, Duration cooldown) {
    /**
     * 拒绝无界或无效设置，不扩大实际交互预算。
     * @param enabled 当前开关
     * @param triggerTurns 触发轮数
     * @param keepTurns 保留轮数
     * @param triggerBytes 触发字节
     * @param maxInputBytes 输入上限
     * @param maxCharacters 正文上限
     * @param maxModelCalls 调用上限
     * @param maxTokens 显式总 token 上限，可为空
     * @param timeout 后台时限
     * @param cooldown 冷却
     * @throws IllegalArgumentException 当预算或时间无效时
     */
    public AgentSummarySettings {
        if (triggerTurns <= keepTurns || keepTurns < 1 || triggerTurns > 100 || triggerBytes < 1 || maxInputBytes < 1024 || maxInputBytes > 1048576
                // 2026/11/04：总 token 配额只在显式配置时生效，零额度明确禁止模型调用。
                || maxCharacters < 1 || maxCharacters > 16000 || maxModelCalls < 1 || maxModelCalls > 10
                || maxTokens != null && maxTokens < 0
                || timeout == null || timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofMinutes(5)) > 0
                || cooldown == null || cooldown.isNegative() || cooldown.compareTo(Duration.ofDays(1)) > 0) {
            throw new IllegalArgumentException("自动摘要触发条件、时间或预算无效。");
        }
    }
}
