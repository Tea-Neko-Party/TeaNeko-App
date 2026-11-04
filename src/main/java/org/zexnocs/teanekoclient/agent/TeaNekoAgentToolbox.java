package org.zexnocs.teanekoclient.agent;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoclient.agent.sender.AgentConversationSender;
import org.zexnocs.teanekoclient.agent.sender.AgentMemoryWriteSender;
import org.zexnocs.teanekoclient.agent.sender.AgentPersonalityCorrectionSender;
import org.zexnocs.teanekoclient.agent.tools.ITeaNekoAgentToolbox;
import org.zexnocs.teanekocore.logger.ILogger;

/**
 * 仅公开 Agent 专属 Sender；普通平台工具保持原不支持边界。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.4.1
 * @version 4.8.0-l
 */
@Getter @Component @RequiredArgsConstructor
public final class TeaNekoAgentToolbox implements ITeaNekoAgentToolbox {
    private final AgentConversationSender agentConversationSender;
    private final AgentMemoryWriteSender agentMemoryWriteSender;
    private final AgentPersonalityCorrectionSender agentPersonalityCorrectionSender;
    private final ILogger logger;
}
