package org.zexnocs.teanekoagent.integration.config;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManager;

/**
 * 读取当前聊天的失败回执开关，与模型及可选能力解耦；注销或关闭后禁止提示发送。
 *
 * @author zExNocs
 * @date 2026/11/05
 * @since 4.8.2-l
 * @version 4.8.2-l
 */
@Service
public final class AgentFailureReplyPolicy {
    private final IConfigDataService configs;
    private final ConfigManager manager = AgentScopeConfigManager.class.getAnnotation(ConfigManager.class);

    /** @param configs 原聊天配置服务 */
    public AgentFailureReplyPolicy(IConfigDataService configs) {
        this.configs = configs;
    }

    /**
     * @param scope 原请求所属聊天
     * @return 当前仍启用且允许失败回执时为真
     */
    public boolean enabled(AgentScope scope) {
        return configs.getConfigData(manager, AgentScopeConfigData.class, scope.scopeId())
                .map(config -> config.isEnabled() && config.isFailureReplyEnabled()).orElse(false);
    }
}
