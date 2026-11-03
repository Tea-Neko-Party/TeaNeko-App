package org.zexnocs.teanekoagent.agent;

/**
 * 统一门控识别的 Agent 活动，入口必须由宿主确定，不能取自模型参数。
 * <br>2026/11/03：隔离零工具的表情包视觉任务。
 *
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.0.0
 * @version 4.6.1-l
 */
public enum AgentActivity {
    // 2026/11/03：素材分析不依赖记忆学习开关，也不借用聊天触发票据。
    SERVICE, COMMAND, AUTOMATIC_REPLY, COLLECT, SUMMARY, PROACTIVE, SCHEDULED, TOOL, LEARNING, STICKER_ANALYSIS
}
