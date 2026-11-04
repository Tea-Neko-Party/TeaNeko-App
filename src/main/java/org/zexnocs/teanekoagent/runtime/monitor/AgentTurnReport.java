package org.zexnocs.teanekoagent.runtime.monitor;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoagent.component.llm.framework.interaction.LLMTokenUsage;
import org.zexnocs.teanekoagent.runtime.AgentActivity;
import org.zexnocs.teanekoagent.runtime.AgentRunRequest;
import org.zexnocs.teanekoagent.runtime.AgentRunResult;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekocore.framework.composition.EntityId;

import java.util.UUID;

/**
 * 轮次结束后的脱敏计数快照，不携带原文、答案、参数、异常链或凭据。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @param requestId 实际请求标识
 * @param agentId 实际执行实例，用于分别聚合监控与告警
 * @param scope 原作用域
 * @param activity 原业务活动
 * @param status 模型轮次状态，不代表平台已发送
 * @param elapsedMillis 实际轮次耗时
 * @param modelCalls 本轮模型尝试数
 * @param toolCalls 本轮工具结果数
 * @param reportedTokens 供应商报告的总用量
 * @param estimatedTokens 保守估算用量，不等同计费
 * @param unknownUsageAttempts 无可用用量的尝试数
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.5.0-l
 * @version 4.8.0-l
 */
public record AgentTurnReport(UUID requestId, EntityId agentId, AgentScope scope, AgentActivity activity, AgentRunResult.Status status,
                              long elapsedMillis, int modelCalls, int toolCalls, long reportedTokens,
                              long estimatedTokens, int unknownUsageAttempts) {
    /**
     * 只统计本轮交互，不叠加共享父预算或把未知用量报告为零。
     * @param request 原请求，仅提取定位
     * @param result 原结果，仅提取计数
     * @param elapsedMillis 已经过的毫秒数
     * @return 不含业务正文的快照
     */
    public static AgentTurnReport from(AgentRunRequest request, AgentRunResult result, long elapsedMillis) {
        var interaction = result.interaction().orElse(null); long reported = 0, estimated = 0; int unknown = 0;
        if (interaction != null) {
            for (var attempt : interaction.attempts()) {
                var usage = attempt.tokenUsage().orElse(null);
                if (usage == null) { unknown++; }
                else if (usage.source() == LLMTokenUsage.Source.REPORTED) { reported = Math.addExact(reported, usage.tokens()); }
                else { estimated = Math.addExact(estimated, usage.tokens()); }
            }
        }
        return new AgentTurnReport(request.requestId(), result.agentId(), request.actor().scope(), request.activity(), result.status(), Math.max(0, elapsedMillis),
                interaction == null ? 0 : interaction.attempts().size(), interaction == null ? 0 : interaction.tools().size(), reported, estimated, unknown);
    }
}
