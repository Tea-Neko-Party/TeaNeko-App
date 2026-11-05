package org.zexnocs.teanekoagent.integration.config;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;

/**
 * 读取当前聊天的失败回执开关，与模型及可选能力解耦；注销或关闭后禁止提示发送。
 *
 * 修改记录：<br>
 * 2026/11/05：通过配置管理器 Bean 读取开关，避免把注解实例交给管理器实例接口。
 *
 * @author zExNocs
 * @date 2026/11/05
 * @since 4.8.2-l
 * @version 4.8.2-l
 */
@Service
public final class AgentFailureReplyPolicy {
    private final IConfigDataService configs;
    private final AgentScopeConfigManager manager;

    /**
     * 2026/11/05：与其他聊天配置策略共用 Spring 管理的管理器实例。
     * @param configs 原聊天配置服务
     * @param manager 声明聊天配置的管理器 Bean
     */
    public AgentFailureReplyPolicy(IConfigDataService configs, AgentScopeConfigManager manager) {
        this.configs = configs;
        this.manager = manager;
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
