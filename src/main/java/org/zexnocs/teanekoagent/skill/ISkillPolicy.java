package org.zexnocs.teanekoagent.skill;

import org.zexnocs.teanekoagent.agent.AgentScope;

/**
 * 从真实配置读取当前 Skill 选择，不能由技能正文或模型覆盖。
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.0.0
 * @version 4.5.0-l
 */
@FunctionalInterface
public interface ISkillPolicy {
    /**
     * 读取当前作用域及全局开关，不创建配置记录。
     * @param scope 真实作用域
     * @return 已验证策略，未登记或禁用返回关闭
     */
    SkillPolicy read(AgentScope scope);
}
