package org.zexnocs.teanekoagent_old.personality.config.app;

import org.zexnocs.teanekoagent_old.file_config.personality.AgentPersonalityConfigData;
import org.zexnocs.teanekoagent_old.personality.config.AgentPersonalityFieldChecker;
import org.zexnocs.teanekoapp.config.TeaNekoConfigNamespaces;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManager;

/**
 * TeaNeko App 侧的 Agent 人格配置管理器注册类。
 * <br>该类只负责把 Agent 人格配置注册进 TeaNeko ConfigData 系统，真正的 Agent Core 不直接依赖该类。
 *
 * @author zExNocs
 * @date 2026/06/09
 * @since 4.4.1
 */
@ConfigManager(
        value = "agent-personality",
        description = "Agent 性格、记忆和模型覆盖配置",
        configType = AgentPersonalityConfigData.class,
        namespaces = {
                TeaNekoConfigNamespaces.GENERAL,
                TeaNekoConfigNamespaces.GROUP,
                TeaNekoConfigNamespaces.PRIVATE
        },
        fieldChecker = AgentPersonalityFieldChecker.class
)
public class TeaNekoAgentPersonalityConfigManager {
}
