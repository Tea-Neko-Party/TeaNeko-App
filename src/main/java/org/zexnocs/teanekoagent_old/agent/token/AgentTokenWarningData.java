package org.zexnocs.teanekoagent_old.agent.token;

import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent_old.agent.event.AgentTurnData;

import java.util.List;

/**
 * Agent token 告警事件数据。
 * <br>该对象在单轮对话完成后由 token 监控器创建，监听器可以读取本轮聚合 token 使用情况并执行额外处理。
 *
 * @author zExNocs
 * @date 2026/06/11
 * @since 4.4.1
 */
@Getter
@Setter
public class AgentTokenWarningData {
    /**
     * 当前 Agent 单轮运行数据。
     */
    private final AgentTurnData turnData;

    /**
     * 本次告警级别。
     */
    private AgentTokenUsageLevel level;

    /**
     * 触发告警的原因说明。
     */
    private String reason;

    /**
     * 本轮对话中的 token 使用记录。
     */
    private List<AgentTokenUsageRecord> records;

    /**
     * 本轮聚合 prompt token 数。
     */
    private int promptTokens;

    /**
     * 本轮聚合 completion token 数。
     */
    private int completionTokens;

    /**
     * 本轮聚合总 token 数。
     */
    private int totalTokens;

    /**
     * 本轮聚合 reasoning token 数。
     */
    private int reasoningTokens;

    /**
     * 关联异常。
     */
    @Nullable
    private Throwable throwable;

    /**
     * 创建 Agent token 告警事件数据。
     *
     * @param turnData  当前 Agent 单轮运行数据。
     * @param level     本次告警级别。
     * @param reason    触发告警的原因说明。
     * @param records   本轮 token 使用记录。
     * @param throwable 关联异常。
     */
    public AgentTokenWarningData(AgentTurnData turnData,
                                 AgentTokenUsageLevel level,
                                 String reason,
                                 List<AgentTokenUsageRecord> records,
                                 @Nullable Throwable throwable) {
        this.turnData = turnData;
        this.level = level;
        this.reason = reason == null ? "" : reason;
        this.records = records == null ? List.of() : List.copyOf(records);
        this.throwable = throwable;
        summarize(this.records);
    }

    /**
     * 汇总 token 使用记录。
     *
     * @param records 需要汇总的 token 使用记录。
     */
    private void summarize(List<AgentTokenUsageRecord> records) {
        promptTokens = 0;
        completionTokens = 0;
        totalTokens = 0;
        reasoningTokens = 0;
        for (var record : records) {
            if (record == null) {
                continue;
            }
            promptTokens += record.getPromptTokens();
            completionTokens += record.getCompletionTokens();
            totalTokens += record.getTotalTokens();
            reasoningTokens += record.getReasoningTokens();
        }
    }
}
