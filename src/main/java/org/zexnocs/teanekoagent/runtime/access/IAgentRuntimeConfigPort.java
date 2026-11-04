package org.zexnocs.teanekoagent.runtime.access;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

/**
 * Agent 本地运行设置的读取端口，构造时不解析配置，避免可修复错误阻断 App 启动。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.5.0-l
 * @version 4.8.0-l
 */
@FunctionalInterface
public interface IAgentRuntimeConfigPort {
    /**
     * 在检查或轮次准备时读取本地固定设置，不返回文件配置可变 Bean。
     * @return 运行设置快照
     * @throws org.zexnocs.teanekoagent.component.llm.framework.model.LLMConfigurationException 当本地配置无效时
     */
    AgentRuntimeConfig read();
}
