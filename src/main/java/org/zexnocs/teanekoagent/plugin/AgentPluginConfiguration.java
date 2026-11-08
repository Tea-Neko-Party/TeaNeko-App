package org.zexnocs.teanekoagent.plugin;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zexnocs.teanekocore.plugin.HostCapability;

import java.util.Set;

/**
 * Agent 宿主能力声明，保留管理资源使用的稳定身份。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/07：明确宿主基础模块的版本、类别和职责。
 * <br>2026/11/07：宿主身份独立于业务插件，不进入插件启停目录。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.0-l
 * @version 4.9.0-l
 */
@Configuration(proxyBeanMethods = false)
public class AgentPluginConfiguration {
    /** 2026/11/07：保留 Agent 管理资源身份。 @return 依赖 Core 的宿主能力 */
    @Bean
    public HostCapability teaNekoAgentCapability() {
        return new HostCapability("teaneko.agent", "4.9.0-l", Set.of("teaneko.core"));
    }
}
