package org.zexnocs.teanekoagent.component.personality;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoagent.runtime.AgentScope;

/**
 * 隔离人格业务与文件/作用域配置实现，不负责模型或记忆查询。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：区分未安装人格和既有默认人格配置。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.5.0-l
 * @version 4.8.0-l
 */
public interface IPersonalityConfiguration {
    /**
     * @param scope 实际聊天
     * @return 2026/11/04：是否安装人格组件；旧独立宿主保持原已装配语义
     */
    default boolean installed(AgentScope scope) {
        return true;
    }
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
