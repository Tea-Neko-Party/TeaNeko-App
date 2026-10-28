package org.zexnocs.teanekoagent.llm.framework.interaction;

/**
 * 调用时解析本地默认设置的端口，独立于 Agent、Prompt 和供应商凭据。
 * 实现不得在构造阶段因可修复的本地配置错误阻断应用启动。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
@FunctionalInterface
public interface ILLMInteractionDefaults {
    /**
     * 为一次交互读取并验证固定设置；同一轮不会再次读取热更新配置。
     *
     * @return 验证后的不可变设置
     * @throws org.zexnocs.teanekoagent.llm.framework.model.LLMConfigurationException 当本地配置不可用时
     */
    LLMInteractionSettings resolve();
}
