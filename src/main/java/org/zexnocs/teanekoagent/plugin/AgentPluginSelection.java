package org.zexnocs.teanekoagent.plugin;

import org.zexnocs.teanekocore.plugin.PluginRegistry;
import java.util.Map;

/**
 * 本轮已经安装的能力及其固定版本，作为不可变组件保存。
 * @param tokens 能力 ID 到插件依赖版本的映射
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.0.0
 * @version 4.6.0-l
 */
public record AgentPluginSelection(Map<String, PluginRegistry.Token> tokens) {
    /**
     * 冻结版本目录。
     * @param tokens 固定版本
     */
    public AgentPluginSelection { tokens = Map.copyOf(tokens); }
}
