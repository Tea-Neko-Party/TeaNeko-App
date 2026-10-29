package org.zexnocs.teanekoagent.agent.conversation;

/**
 * 单轮固定的历史与重放策略，窗口和诊断缓存分别受限，不表示供应商 token 预算。
 * @param maxHistoryMessages 最近完整成功轮次的消息上限，零关闭窗口
 * @param maxInputBytes 统一协议 JSON 的输入字节上限
 * @param maxCompletedRequests 最近完成记录数量上限
 * @param maxReplayBytes 可保留结果正文的序列化度量预算，零仅保留状态
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record AgentConversationOptions(int maxHistoryMessages, long maxInputBytes, int maxCompletedRequests, long maxReplayBytes) {
    /**
     * 校验独立限额，禁止负窗口、无输入上限或无完成记录容量。
     * @param maxHistoryMessages 非负历史消息数
     * @param maxInputBytes 正输入字节数
     * @param maxCompletedRequests 正完成记录数
     * @param maxReplayBytes 非负结果字节预算
     * @throws IllegalArgumentException 当任一限制越界时
     */
    public AgentConversationOptions {
        if (maxHistoryMessages < 0 || maxInputBytes < 1 || maxCompletedRequests < 1 || maxReplayBytes < 0) {
            throw new IllegalArgumentException("会话窗口、输入和重放限制不满足有效范围。");
        }
    }

    /**
     * 返回不读取外部配置的默认策略。
     * @return 四十条消息、二百五十六 KiB 输入、三十二条完成记录及一 MiB 重放预算
     */
    public static AgentConversationOptions defaults() { return new AgentConversationOptions(40, 262144, 32, 1048576); }
}
