package org.zexnocs.teanekoagent.llm.framework.interaction;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

/**
 * 调用时解析本地默认设置的端口，独立于 Agent、Prompt 和供应商凭据。
 * 实现不得在构造阶段因可修复的本地配置错误阻断应用启动。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：迁入独立 LLM 子模块，保留公开协议与原配置语义。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.5.0-l
 * @version 4.9.0-l
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
