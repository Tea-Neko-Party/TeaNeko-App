package org.zexnocs.teanekoagent.extension;

/**
 * 后端业务回执，已开始但未确认的写入或执行不能冒充已回滚或可安全重试。
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.0.0
 * @version 4.5.0-l
 */
public enum AgentExtensionStatus { COMPLETED, REJECTED, INDETERMINATE }
