package org.zexnocs.teanekoplugin.agent.automation.extension;

// 2026/11/07：按真实职责分离通用来源、防重底座与插件业务，持久标识保持不变。
// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

/**
 * 可信宿主显式选择的扩展操作，名称只描述能力，不代表已获得权限。
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
public enum AgentExtensionCapability {
    FILE_READ, FILE_WRITE, CODE_EXECUTE, KNOWLEDGE_SEARCH;
    /** @return 对应的稳定工具名称 */
    public String toolName() { return name().toLowerCase(java.util.Locale.ROOT); }
}
