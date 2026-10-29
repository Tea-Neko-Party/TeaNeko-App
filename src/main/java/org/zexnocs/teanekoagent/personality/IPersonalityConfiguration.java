package org.zexnocs.teanekoagent.personality;

import org.zexnocs.teanekoagent.agent.AgentScope;

/**
 * 隔离人格业务与文件/作用域配置实现，不负责模型或记忆查询。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public interface IPersonalityConfiguration {
    /**
     * 读取实际登记的当前策略。
     * @param scope 真实作用域
     * @return 固定配置，缺失或停用须拒绝
     */
    PersonalitySettings settings(AgentScope scope);
    /**
     * 按本地配置及作用域覆盖冻结基础人格。
     * @param settings 本轮策略
     * @return 已校验基础人格
     */
    BasePersonality base(PersonalitySettings settings);
}
