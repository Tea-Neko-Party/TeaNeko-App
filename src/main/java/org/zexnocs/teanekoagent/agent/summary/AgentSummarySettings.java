package org.zexnocs.teanekoagent.agent.summary;

import java.time.Duration;

/**
 * 独立摘要预算与触发规则，构造校验在实际读取时进行，不阻止非 Agent 功能启动。
 * @param enabled 真实登记且允许自动摘要
 * @param triggerTurns 触发轮次数
 * @param keepTurns 保留最近完整轮次数
 * @param triggerBytes 触发协议字节数
 * @param maxInputBytes 摘要输入完整协议字节上限
 * @param maxCharacters 摘要正文上限
 * @param maxModelCalls 独立交互模型请求上限
 * @param maxTokens 独立交互总 token 预算
 * @param timeout 独立后台总时限
 * @param cooldown 同会话生成冷却
 * @author zExNocs
 * @date 2026/10/30
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record AgentSummarySettings(boolean enabled, int triggerTurns, int keepTurns, long triggerBytes, long maxInputBytes,
        int maxCharacters, int maxModelCalls, long maxTokens, Duration timeout, Duration cooldown) {
    /**
     * 拒绝无界或无效设置，不扩大实际交互预算。
     * @param enabled 当前开关
     * @param triggerTurns 触发轮数
     * @param keepTurns 保留轮数
     * @param triggerBytes 触发字节
     * @param maxInputBytes 输入上限
     * @param maxCharacters 正文上限
     * @param maxModelCalls 调用上限
     * @param maxTokens 总 token 上限
     * @param timeout 后台时限
     * @param cooldown 冷却
     * @throws IllegalArgumentException 当预算或时间无效时
     */
    public AgentSummarySettings {
        if (triggerTurns <= keepTurns || keepTurns < 1 || triggerTurns > 100 || triggerBytes < 1 || maxInputBytes < 1024 || maxInputBytes > 1048576
                || maxCharacters < 1 || maxCharacters > 16000 || maxModelCalls < 1 || maxModelCalls > 10 || maxTokens < 1
                || timeout == null || timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofMinutes(5)) > 0
                || cooldown == null || cooldown.isNegative() || cooldown.compareTo(Duration.ofDays(1)) > 0) {
            throw new IllegalArgumentException("自动摘要触发条件、时间或预算无效。");
        }
    }
}
