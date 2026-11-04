package org.zexnocs.teanekoagent.runtime;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

/**
 * 统一门控识别的 Agent 活动，入口必须由宿主确定，不能取自模型参数。
 * <br>2026/11/03：隔离零工具的表情包视觉任务。
 *
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.5.0-l
 * @version 4.8.0-l
 */
public enum AgentActivity {
    // 2026/11/03：素材分析不依赖记忆学习开关，也不借用聊天触发票据。
    SERVICE, COMMAND, AUTOMATIC_REPLY, COLLECT, SUMMARY, PROACTIVE, SCHEDULED, TOOL, LEARNING, STICKER_ANALYSIS
}
