package org.zexnocs.teanekoagent.agent.access;

import org.zexnocs.teanekoagent.agent.AgentActivity;
import org.zexnocs.teanekoagent.agent.AgentActorContext;

/**
 * 宿主活动的动态准入补充，不替代 scope、实例、记忆或实际工具权限。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.5.0-l
 * @version 4.5.0-l
 */
@FunctionalInterface
public interface IAgentActivityPolicy {
    /**
     * 在每次原管线门控时复核宿主拥有关系；查询失败必须拒绝。
     * @param actor 原可信来源
     * @param activity 实际检查活动
     * @return 当前允许时为真
     */
    boolean allows(AgentActorContext actor, AgentActivity activity);
}
