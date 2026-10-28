package org.zexnocs.teanekoagent.agent;

/**
 * 统一门控识别的 Agent 活动，入口必须由宿主确定，不能取自模型参数。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public enum AgentActivity {
    SERVICE, COMMAND, AUTOMATIC_REPLY, COLLECT, SUMMARY, PROACTIVE, SCHEDULED, TOOL
}
