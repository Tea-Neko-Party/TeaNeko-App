package org.zexnocs.teanekoplugin.agent_example;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zexnocs.teanekoagent.plugin.AgentFeature;
import org.zexnocs.teanekocore.plugin.PluginDescriptor;
import org.zexnocs.teanekocore.plugin.PluginModule;

import java.time.Clock;
import java.util.Set;

/**
 * 默认不装配的可编译插件示例；配置启用后仍需实例显式选择能力。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.0-l
 * @version 4.6.0-l
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "tea-neko.plugins.example.enabled", havingValue = "true")
public class ExampleAgentPlugin {
    /** @return 示例插件元数据 */
    @Bean public PluginModule exampleAgentModule() { return () -> new PluginDescriptor("example.clock", "1.0.0", 1, Set.of("teaneko.agent")); }
    /** @return 示例能力，不默认安装到所有实例 */
    @Bean public AgentFeature exampleClockFeature() { return new ExampleClockFeature(Clock.systemUTC()); }
}
