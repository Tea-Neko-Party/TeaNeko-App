package org.zexnocs.teanekoagent_old.client.tools;

import org.zexnocs.teanekoagent_old.sender.AgentConversationSender;
import org.zexnocs.teanekoagent_old.sender.AgentMemoryWriteSender;
import org.zexnocs.teanekoagent_old.sender.AgentPersonalityCorrectionSender;
import org.zexnocs.teanekoapp.client.tools.ITeaNekoToolbox;

/**
 * TeaNeko Agent 内置客户端工具箱。
 * <br>仅暴露 Agent 专属 sender；平台消息、群成员等无关工具继续由父接口抛出不支持异常。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
public interface ITeaNekoAgentToolbox extends ITeaNekoToolbox {
    /** @return Agent 手动对话 sender */
    AgentConversationSender getAgentConversationSender();

    /** @return Agent 手动记忆写入 sender */
    AgentMemoryWriteSender getAgentMemoryWriteSender();

    /** @return Agent 手动人格修正 sender */
    AgentPersonalityCorrectionSender getAgentPersonalityCorrectionSender();
}
