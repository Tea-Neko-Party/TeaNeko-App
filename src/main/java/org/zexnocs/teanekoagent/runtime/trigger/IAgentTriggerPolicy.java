package org.zexnocs.teanekoagent.runtime.trigger;

import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekoagent.runtime.access.AgentScopePolicy;

/**
 * 为自动触发门控提供可替换的活动限制，不向宿主公开业务字段或组件标识。
 * <br>2026/11/07：活动插件拥有参数解释，宿主保留票据、时限和发送前复核。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
@FunctionalInterface
public interface IAgentTriggerPolicy {
    /**
     * 按实际聊天读取当前活动资格，停用时返回禁止主动活动的策略。
     * @param scope 真实聊天定位
     * @param policy 本次已读取的宿主准入策略
     * @return 当前触发设置
     */
    AgentTriggerGate.Settings resolve(AgentScope scope, AgentScopePolicy policy);
}
