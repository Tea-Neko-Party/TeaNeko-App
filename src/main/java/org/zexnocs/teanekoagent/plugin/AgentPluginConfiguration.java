package org.zexnocs.teanekoagent.plugin;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zexnocs.teanekocore.plugin.PluginDescriptor;
import org.zexnocs.teanekocore.plugin.PluginModule;

import java.util.Set;

/**
 * Agent 宿主模块声明，复用 Core 插件目录。
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.0.0
 * @version 4.6.0-l
 */
@Configuration(proxyBeanMethods = false)
public class AgentPluginConfiguration {
    /** @return 依赖 Core 的 Agent 宿主模块 */
    @Bean public PluginModule teaNekoAgentModule() {
        return () -> new PluginDescriptor("teaneko.agent", "4.6.0-l", 1, Set.of("teaneko.core"), true);
    }
}
