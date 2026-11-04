package org.zexnocs.teanekoclient.agent.tools;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoapp.client.tools.ITeaNekoToolbox;
import org.zexnocs.teanekoclient.agent.sender.AgentConversationSender;
import org.zexnocs.teanekoclient.agent.sender.AgentMemoryWriteSender;
import org.zexnocs.teanekoclient.agent.sender.AgentPersonalityCorrectionSender;

/**
 * 内置 Agent 客户端能力，不声明机器人平台身份或普通消息发送器。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.4.1
 * @version 4.8.0-l
 */
public interface ITeaNekoAgentToolbox extends ITeaNekoToolbox {
    /**
     * 获取显式对话入口。
     * @return 只返回业务答案的 Sender
     */
    AgentConversationSender getAgentConversationSender();
    /**
     * 获取本人明确记忆新增入口。
     * @return 受原文意图限制的 Sender
     */
    AgentMemoryWriteSender getAgentMemoryWriteSender();
    /**
     * 获取管理员表达候选入口。
     * @return 受当前人格边界限制的 Sender
     */
    AgentPersonalityCorrectionSender getAgentPersonalityCorrectionSender();
}
