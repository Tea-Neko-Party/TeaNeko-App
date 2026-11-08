package org.zexnocs.teanekoplugin.agent.automation.extension;

// 2026/11/07：按真实职责分离通用来源、防重底座与插件业务，持久标识保持不变。
// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

/**
 * 后端业务回执，已开始但未确认的写入或执行不能冒充已回滚或可安全重试。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * <br>2026/11/07：业务归属与通用底座按职责分离，保留持久身份及完成边界。
 *
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
public enum AgentExtensionStatus { COMPLETED, REJECTED, INDETERMINATE }
