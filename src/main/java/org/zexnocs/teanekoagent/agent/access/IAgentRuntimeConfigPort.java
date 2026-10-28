package org.zexnocs.teanekoagent.agent.access;

/**
 * Agent 本地运行设置的读取端口，构造时不解析配置，避免可修复错误阻断 App 启动。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
@FunctionalInterface
public interface IAgentRuntimeConfigPort {
    /**
     * 在检查或轮次准备时读取本地固定设置，不返回文件配置可变 Bean。
     * @return 运行设置快照
     * @throws org.zexnocs.teanekoagent.llm.framework.model.LLMConfigurationException 当本地配置无效时
     */
    AgentRuntimeConfig read();
}
