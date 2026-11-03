package org.zexnocs.teanekoagent.extension;

/**
 * 可信宿主显式选择的扩展操作，名称只描述能力，不代表已获得权限。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.5.0-l
 * @version 4.5.0-l
 */
public enum AgentExtensionCapability {
    FILE_READ, FILE_WRITE, CODE_EXECUTE, KNOWLEDGE_SEARCH;
    /** @return 对应的稳定工具名称 */
    public String toolName() { return name().toLowerCase(java.util.Locale.ROOT); }
}
