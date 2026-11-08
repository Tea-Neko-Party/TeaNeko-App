package org.zexnocs.teanekoplugin.agent.social.proactive;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.integration.config.AgentComponentAccess;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigData;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigManager;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekoagent.runtime.access.AgentScopePolicy;
import org.zexnocs.teanekoagent.runtime.trigger.AgentTriggerGate;
import org.zexnocs.teanekoagent.runtime.trigger.IAgentTriggerPolicy;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;

import java.util.Optional;

/**
 * 解释主动活动组件的参数，供候选调度与通用触发门控复用同一配置语义。
 * <br>2026/11/07：配置缺失、停用或依赖失效时明确关闭，不向宿主增加业务访问器。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
@Service
public final class ProactiveConfiguration implements IAgentTriggerPolicy {
    private final IConfigDataService configs;
    private final AgentScopeConfigManager manager;
    private final AgentComponentAccess components;

    /**
     * 借用权威配置与已发布组件目录，不在构造时查询聊天。
     * @param configs 当前聊天配置
     * @param manager 宿主配置声明
     * @param components 当前有效装配
     */
    public ProactiveConfiguration(IConfigDataService configs, AgentScopeConfigManager manager,
                                  AgentComponentAccess components) {
        this.configs = configs;
        this.manager = manager;
        this.components = components;
    }

    /**
     * 读取当前聊天的有效主动活动设置。
     * @param scopeId 真实已登记聊天键
     * @return 只有 ACTIVE 模式且组件有效时提供设置
     */
    public Optional<Settings> read(String scopeId) {
        return configs.getConfigData(manager, AgentScopeConfigData.class, scopeId)
                .filter(config -> config.toPolicy().mode() == AgentScopePolicy.Mode.ACTIVE)
                .flatMap(config -> components.settings(config, "proactive"))
                .map(value -> new Settings(value.path("proactiveTimedEnabled").asBoolean(true),
                        value.path("proactiveInterjectionEnabled").asBoolean(true),
                        value.path("activityTimeZone").asString("UTC"),
                        value.path("proactiveCooldownSeconds").asInt(900),
                        value.path("proactiveIntervalSeconds").asInt(900),
                        value.path("proactiveAttemptCooldownSeconds").asInt(60),
                        value.path("proactiveQuietStartHour").asInt(23),
                        value.path("proactiveQuietEndHour").asInt(7)));
    }

    /**
     * 将本插件有效活动配置适配为宿主的触发限制。
     * @param scope 真实聊天
     * @param policy 本次宿主准入
     * @return 活动有效时的限制或明确被动策略
     */
    @Override
    public AgentTriggerGate.Settings resolve(AgentScope scope, AgentScopePolicy policy) {
        return read(scope.scopeId()).map(value -> new AgentTriggerGate.Settings(policy, true,
                        value.zone(), value.cooldownSeconds(), value.quietStartHour(), value.quietEndHour()))
                .orElseGet(() -> AgentTriggerGate.Settings.passive(policy));
    }

    /**
     * 主动活动组件的一次不可变参数读取，不携带宿主或其他插件配置。
     * @param timed 定时候选开关
     * @param interjection 接话候选开关
     * @param zone 静默时区
     * @param cooldownSeconds 成功发言冷却
     * @param intervalSeconds 定时扫描间隔
     * @param attemptCooldownSeconds 失败尝试退避
     * @param quietStartHour 静默起点
     * @param quietEndHour 静默终点
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    public record Settings(boolean timed, boolean interjection, String zone, int cooldownSeconds,
                           int intervalSeconds, int attemptCooldownSeconds, int quietStartHour, int quietEndHour) {
    }
}
