package org.zexnocs.teanekoagent.component.tool.extension;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

/**
 * 后端业务回执，已开始但未确认的写入或执行不能冒充已回滚或可安全重试。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.5.0-l
 * @version 4.8.0-l
 */
public enum AgentExtensionStatus { COMPLETED, REJECTED, INDETERMINATE }
