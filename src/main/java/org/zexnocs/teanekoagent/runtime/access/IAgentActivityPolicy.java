package org.zexnocs.teanekoagent.runtime.access;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoagent.runtime.AgentActivity;
import org.zexnocs.teanekoagent.runtime.AgentActorContext;

/**
 * 宿主活动的动态准入补充，不替代 scope、实例、记忆或实际工具权限。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.5.0-l
 * @version 4.8.0-l
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
